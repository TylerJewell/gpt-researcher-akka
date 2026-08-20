package io.akka.gptresearcher.application;

/**
 * SPEC-001 question-log rows 3–4. Defaults mirror the source's own defaults, read from
 * {@code gpt_researcher/config/variables/default.py}: similarity threshold 0.42, 10 kept
 * docs per sub-query, curation off by default and capped at 10 sources when it runs.
 */
public record ResearchConfig(
    double similarityThreshold, int maxResultsPerSubQuery, boolean curateSources, int maxCuratedSources) {

  public static ResearchConfig defaults() {
    return new ResearchConfig(0.42, 10, false, 10);
  }
}
