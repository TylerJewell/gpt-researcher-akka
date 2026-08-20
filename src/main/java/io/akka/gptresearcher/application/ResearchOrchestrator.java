package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.ResearchContext;
import io.akka.gptresearcher.domain.ScoredDoc;
import io.akka.gptresearcher.domain.SourceDoc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plan, fan out, gather, reconcile, stop — see {@code specs/SPEC-001-gpt-researcher.md}.
 *
 * <p>Ported from {@code gpt_researcher/skills/researcher.py}'s {@code
 * ResearchConductor.conduct_research} / {@code _get_context_by_web_search}: a single pass,
 * not a loop (rule 7). The source's shared, single-threaded {@code visited_urls} set is
 * replaced by a {@link ConcurrentHashMap}-backed key set so the same dedup invariant (rule 3)
 * holds under true parallel execution, not just Python's cooperative concurrency.
 */
public class ResearchOrchestrator {

  private final QueryPlanner planner;
  private final SearchGatherer gatherer;
  private final Scorer scorer;
  private final ReconcileCurator curator;

  public ResearchOrchestrator(
      QueryPlanner planner, SearchGatherer gatherer, Scorer scorer, ReconcileCurator curator) {
    this.planner = planner;
    this.gatherer = gatherer;
    this.scorer = scorer;
    this.curator = curator;
  }

  public ResearchContext conductResearch(String query, ResearchConfig cfg) {
    // Plan (rule 1): initial search, then sub-queries from it, plus the original query.
    List<SourceDoc> initialResults = gatherer.search(query);
    List<String> subQueries = new ArrayList<>(planner.plan(query, initialResults));
    if (!subQueries.contains(query)) {
      subQueries.add(query);
    }

    Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    // Fan out (rule 2): every sub-query gathers independently and concurrently.
    List<CompletableFuture<List<ScoredDoc>>> futures =
        subQueries.stream()
            .map(sq -> CompletableFuture.supplyAsync(() -> gatherOne(sq, cfg, visitedUrls)))
            .toList();

    List<ScoredDoc> allKept = new ArrayList<>();
    for (CompletableFuture<List<ScoredDoc>> f : futures) {
      // Rule 5: a sub-query's empty result contributes nothing to the join.
      allKept.addAll(f.join());
    }

    if (allKept.isEmpty()) {
      return ResearchContext.empty(subQueries);
    }

    boolean curated = false;
    List<ScoredDoc> finalDocs = allKept;
    if (cfg.curateSources()) {
      // Rule 6: opt-in reconcile, falls back to the un-curated set on failure.
      try {
        finalDocs = curator.curate(query, allKept, cfg.maxCuratedSources());
        curated = true;
      } catch (RuntimeException e) {
        finalDocs = allKept;
        curated = false;
      }
    }

    String text = joinContext(finalDocs);
    // Stop (rule 7): return unconditionally — no re-planning based on what was gathered.
    return new ResearchContext(text, finalDocs.size(), curated, subQueries);
  }

  /** Rule 3 (dedup) + rule 4 (threshold, cap) for one sub-query. */
  private List<ScoredDoc> gatherOne(String subQuery, ResearchConfig cfg, Set<String> visitedUrls) {
    List<SourceDoc> found = gatherer.search(subQuery);
    List<ScoredDoc> kept = new ArrayList<>();
    for (SourceDoc doc : found) {
      if (!visitedUrls.add(doc.url())) {
        continue;
      }
      double score = scorer.score(subQuery, doc);
      if (score >= cfg.similarityThreshold()) {
        kept.add(new ScoredDoc(doc, score));
      }
    }
    kept.sort(Comparator.comparingDouble(ScoredDoc::score).reversed());
    return kept.size() > cfg.maxResultsPerSubQuery() ? kept.subList(0, cfg.maxResultsPerSubQuery()) : kept;
  }

  private String joinContext(List<ScoredDoc> docs) {
    StringBuilder sb = new StringBuilder();
    for (ScoredDoc d : docs) {
      if (d.doc().content() == null || d.doc().content().isBlank()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(d.doc().content());
    }
    return sb.toString();
  }
}
