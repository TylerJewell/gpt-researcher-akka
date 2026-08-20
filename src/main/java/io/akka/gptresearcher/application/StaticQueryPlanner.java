package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.SourceDoc;
import java.util.List;

/**
 * The port's own stand-in for the source's strategic-LLM sub-query generation — every
 * distinct URL title from the initial search becomes a sub-query, capped at a fixed count.
 * Deterministic and network-free; a real deployment would inject an LLM-backed
 * {@link QueryPlanner} instead.
 */
public class StaticQueryPlanner implements QueryPlanner {

  private final int maxSubQueries;

  public StaticQueryPlanner(int maxSubQueries) {
    this.maxSubQueries = maxSubQueries;
  }

  @Override
  public List<String> plan(String query, List<SourceDoc> initialResults) {
    return initialResults.stream().map(SourceDoc::title).distinct().limit(maxSubQueries).toList();
  }
}
