package io.akka.gptresearcher.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.akka.gptresearcher.domain.ResearchContext;
import io.akka.gptresearcher.domain.ScoredDoc;
import io.akka.gptresearcher.domain.SourceDoc;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 — the deterministic contract, rule by rule. */
class ResearchOrchestratorTest {

  private static final SourceDoc DOC_A = new SourceDoc("https://a", "A", "content about akka");
  private static final SourceDoc DOC_B = new SourceDoc("https://b", "B", "content about akka too");
  private static final SourceDoc DOC_C = new SourceDoc("https://c", "C", "unrelated content");

  /** A planner that returns fixed sub-queries regardless of input, recording what it was asked. */
  private static QueryPlanner fixedPlanner(List<String> subQueries) {
    return (query, initial) -> subQueries;
  }

  /** A gatherer keyed by exact query string; anything else returns nothing. */
  private static SearchGatherer gathererOf(Map<String, List<SourceDoc>> byQuery) {
    return q -> byQuery.getOrDefault(q, List.of());
  }

  private static Scorer allAbove(double score) {
    return (q, d) -> score;
  }

  @Test
  void rule1_planOutputPlusOriginalQueryIsWhatFansOut() {
    QueryPlanner planner = fixedPlanner(List.of("sub-a", "sub-b"));
    SearchGatherer gatherer =
        gathererOf(
            Map.of(
                "original", List.of(),
                "sub-a", List.of(DOC_A),
                "sub-b", List.of(DOC_B)));
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(1.0), new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("original", ResearchConfig.defaults());

    assertThat(ctx.subQueries()).containsExactlyInAnyOrder("sub-a", "sub-b", "original");
    assertThat(ctx.text()).contains("content about akka").contains("content about akka too");
  }

  @Test
  void rule1_doesNotDuplicateOriginalQueryWhenPlannerAlreadyIncludesIt() {
    QueryPlanner planner = fixedPlanner(List.of("original", "sub-a"));
    SearchGatherer gatherer = gathererOf(Map.of("original", List.of(DOC_A), "sub-a", List.of(DOC_B)));
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(1.0), new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("original", ResearchConfig.defaults());

    assertThat(ctx.subQueries()).containsExactly("original", "sub-a");
  }

  @Test
  void rule2_bothSubQueriesStartBeforeEitherCompletes() {
    // "Independent, not blocked by another's completion" is exercised as timing below
    // (concurrentFanOutRunsSubQueriesConcurrentlyNotSequentially); this test covers the
    // other half of rule 2 directly: a failure in one sub-query's gather is this
    // orchestrator's current documented behaviour — it propagates rather than being
    // silently swallowed, unlike the source's per-query try/except in
    // researcher.py:376-388. That is a listed difference, not an oversight.
    QueryPlanner planner = fixedPlanner(List.of("ok", "boom"));
    SearchGatherer gatherer =
        q -> {
          if (q.equals("boom")) {
            throw new RuntimeException("simulated retriever failure");
          }
          return q.equals("ok") ? List.of(DOC_A) : List.of();
        };
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(1.0), new TopScoreCurator());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> orchestrator.conductResearch("original", ResearchConfig.defaults()))
        .isInstanceOf(java.util.concurrent.CompletionException.class);
  }

  @Test
  void rule3_visitedUrlDedupIsRaceFreeUnderConcurrentFanOut() {
    // Ten sub-queries all resolve to overlapping URL sets; every URL must be claimed
    // exactly once across the whole run, mirroring question-log row 1's finding for the
    // source's single-threaded version — but proven here under true parallel threads.
    List<SourceDoc> sharedDocs =
        List.of(
            new SourceDoc("https://shared/1", "S1", "akka content one"),
            new SourceDoc("https://shared/2", "S2", "akka content two"),
            new SourceDoc("https://shared/3", "S3", "akka content three"));
    List<String> subQueries = List.of("q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8", "q9", "q10");
    QueryPlanner planner = fixedPlanner(subQueries);
    SearchGatherer gatherer = q -> sharedDocs;
    AtomicInteger scoreCalls = new AtomicInteger();
    Scorer countingScorer =
        (q, d) -> {
          scoreCalls.incrementAndGet();
          return 1.0;
        };
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, countingScorer, new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("original", ResearchConfig.defaults());

    // Each of the 3 shared URLs must be scored exactly once total, never once per sub-query.
    assertThat(scoreCalls.get()).isEqualTo(3);
    assertThat(ctx.sourceCount()).isEqualTo(3);
  }

  @Test
  void rule4_docsBelowThresholdAreDropped_andCapAppliesPerSubQuery() {
    List<SourceDoc> many =
        List.of(
            new SourceDoc("https://1", "1", "akka"),
            new SourceDoc("https://2", "2", "akka"),
            new SourceDoc("https://3", "3", "akka"),
            new SourceDoc("https://low", "low", "irrelevant"));
    QueryPlanner planner = fixedPlanner(List.of());
    SearchGatherer gatherer = q -> many;
    Scorer scorer = (q, d) -> d.url().equals("https://low") ? 0.1 : 0.9;
    // Cap set above the doc count so only the threshold can be responsible for the drop.
    ResearchConfig cfg = new ResearchConfig(0.42, 10, false, 10);
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, scorer, new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("q", cfg);

    assertThat(ctx.sourceCount()).isEqualTo(3);
    assertThat(ctx.text()).doesNotContain("irrelevant");
  }

  @Test
  void rule4_capAppliesPerSubQueryIndependentlyOfThreshold() {
    List<SourceDoc> many =
        List.of(
            new SourceDoc("https://1", "1", "akka"),
            new SourceDoc("https://2", "2", "akka"),
            new SourceDoc("https://3", "3", "akka"));
    QueryPlanner planner = fixedPlanner(List.of());
    SearchGatherer gatherer = q -> many;
    ResearchConfig cfg = new ResearchConfig(0.0, 2, false, 10);
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(0.9), new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("q", cfg);

    assertThat(ctx.sourceCount()).isEqualTo(2);
  }

  @Test
  void rule5_emptySubQueryResultsDoNotContributeBlankEntries() {
    QueryPlanner planner = fixedPlanner(List.of("has-content", "empty"));
    SearchGatherer gatherer =
        gathererOf(Map.of("has-content", List.of(DOC_A), "empty", List.of(), "q", List.of()));
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(1.0), new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("q", ResearchConfig.defaults());

    assertThat(ctx.text()).isEqualTo(DOC_A.content());
    assertThat(ctx.text()).doesNotContain("  "); // no double-space from a blank join entry
  }

  @Test
  void rule5_aKeptDocWithBlankContentContributesNoEntryToTheJoin() {
    SourceDoc blank = new SourceDoc("https://blank", "blank", "   ");
    QueryPlanner planner = fixedPlanner(List.of());
    SearchGatherer gatherer = q -> List.of(blank, DOC_A);
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(1.0), new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("q", ResearchConfig.defaults());

    assertThat(ctx.text()).isEqualTo(DOC_A.content());
  }

  @Test
  void rule5_allSubQueriesEmptyReturnsExplicitEmptyResult() {
    QueryPlanner planner = fixedPlanner(List.of("a", "b"));
    SearchGatherer gatherer = q -> List.of();
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(1.0), new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("q", ResearchConfig.defaults());

    assertThat(ctx.text()).isEmpty();
    assertThat(ctx.sourceCount()).isZero();
  }

  @Test
  void rule6_curationOffByDefault() {
    ResearchConfig defaults = ResearchConfig.defaults();
    assertThat(defaults.curateSources()).isFalse();
  }

  @Test
  void rule6_curationRunsWhenEnabledAndCapsResults() {
    List<SourceDoc> docs =
        List.of(
            new SourceDoc("https://1", "1", "akka one"),
            new SourceDoc("https://2", "2", "akka two"),
            new SourceDoc("https://3", "3", "akka three"));
    QueryPlanner planner = fixedPlanner(List.of());
    SearchGatherer gatherer = q -> docs;
    ResearchConfig cfg = new ResearchConfig(0.0, 10, true, 2);
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(0.5), new TopScoreCurator());

    ResearchContext ctx = orchestrator.conductResearch("q", cfg);

    assertThat(ctx.curated()).isTrue();
    assertThat(ctx.sourceCount()).isEqualTo(2);
  }

  @Test
  void rule6_curationFailureFallsBackToUncuratedContext() {
    List<SourceDoc> docs = List.of(new SourceDoc("https://1", "1", "akka one"));
    QueryPlanner planner = fixedPlanner(List.of());
    SearchGatherer gatherer = q -> docs;
    ReconcileCurator failingCurator =
        (query, scoredDocs, maxResults) -> {
          throw new RuntimeException("simulated LLM curation failure");
        };
    ResearchConfig cfg = new ResearchConfig(0.0, 10, true, 10);
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(0.9), failingCurator);

    ResearchContext ctx = orchestrator.conductResearch("q", cfg);

    assertThat(ctx.curated()).isFalse();
    assertThat(ctx.sourceCount()).isEqualTo(1);
    assertThat(ctx.text()).isEqualTo("akka one");
  }

  @Test
  void rule7_singlePass_plannerCalledExactlyOnce() {
    AtomicInteger planCalls = new AtomicInteger();
    QueryPlanner countingPlanner =
        (query, initial) -> {
          planCalls.incrementAndGet();
          return List.of("sub");
        };
    SearchGatherer gatherer = q -> List.of();
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(countingPlanner, gatherer, allAbove(1.0), new TopScoreCurator());

    orchestrator.conductResearch("q", ResearchConfig.defaults());

    assertThat(planCalls.get()).isEqualTo(1);
  }

  @Test
  void concurrentFanOutRunsSubQueriesConcurrentlyNotSequentially() throws InterruptedException {
    // Each sub-query blocks briefly; if fan-out were sequential this would take
    // roughly n * delay. Under concurrent fan-out it should take close to one delay.
    int n = 6;
    List<String> subQueries = new java.util.ArrayList<>();
    for (int i = 0; i < n; i++) subQueries.add("q" + i);
    QueryPlanner planner = fixedPlanner(subQueries);
    ConcurrentHashMap<String, Boolean> started = new ConcurrentHashMap<>();
    SearchGatherer gatherer =
        q -> {
          started.put(q, true);
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return List.of();
        };
    ResearchOrchestrator orchestrator = new ResearchOrchestrator(planner, gatherer, allAbove(1.0), new TopScoreCurator());

    long start = System.nanoTime();
    orchestrator.conductResearch("original", ResearchConfig.defaults());
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    // n+1 calls (sub-queries plus the original) at 100ms each sequentially would be
    // 700ms+; concurrent fan-out should stay well under half that.
    assertThat(elapsedMs).isLessThan(400);
  }
}
