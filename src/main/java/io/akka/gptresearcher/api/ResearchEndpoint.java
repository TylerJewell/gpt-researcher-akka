package io.akka.gptresearcher.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import io.akka.gptresearcher.application.InMemoryCorpusGatherer;
import io.akka.gptresearcher.application.ResearchConfig;
import io.akka.gptresearcher.application.ResearchOrchestrator;
import io.akka.gptresearcher.application.SeedCorpus;
import io.akka.gptresearcher.application.StaticQueryPlanner;
import io.akka.gptresearcher.application.TopScoreCurator;
import io.akka.gptresearcher.application.WordOverlapScorer;
import io.akka.gptresearcher.domain.ResearchContext;
import java.util.List;

/**
 * The research orchestration slice, over HTTP — see {@code specs/SPEC-001-gpt-researcher.md}.
 *
 * <p>Wired here with the port's own in-memory stand-ins for the retriever, scraper, embedding
 * scorer and source curator (SPEC-001 §1) so the endpoint is reachable and demonstrable
 * without any external API credentials. Swapping in real, credentialed implementations of
 * {@code QueryPlanner}/{@code SearchGatherer}/{@code Scorer}/{@code ReconcileCurator} is a
 * wiring change, not a change to {@code ResearchOrchestrator} itself.
 *
 * <p>Opened up for access from the public internet to make this port easy to try out; a
 * production service would scope this more tightly (see {@code akka-sdk} access-control docs).
 */
@HttpEndpoint("/research")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ResearchEndpoint extends AbstractHttpEndpoint {

  private final ResearchOrchestrator orchestrator =
      new ResearchOrchestrator(
          new StaticQueryPlanner(3),
          new InMemoryCorpusGatherer(SeedCorpus.docs()),
          new WordOverlapScorer(),
          new TopScoreCurator());

  public record ResearchRequest(String query, Boolean curateSources) {}

  public record ResearchResponse(
      String query, String context, int sourceCount, boolean curated, List<String> subQueries) {
    static ResearchResponse of(String query, ResearchContext ctx) {
      return new ResearchResponse(query, ctx.text(), ctx.sourceCount(), ctx.curated(), ctx.subQueries());
    }
  }

  @Post
  public ResearchResponse research(ResearchRequest request) {
    if (request.query() == null || request.query().isBlank()) {
      throw HttpException.badRequest("query must not be blank");
    }
    ResearchConfig defaults = ResearchConfig.defaults();
    ResearchConfig cfg =
        new ResearchConfig(
            defaults.similarityThreshold(),
            defaults.maxResultsPerSubQuery(),
            Boolean.TRUE.equals(request.curateSources()),
            defaults.maxCuratedSources());
    ResearchContext ctx = orchestrator.conductResearch(request.query(), cfg);
    return ResearchResponse.of(request.query(), ctx);
  }
}
