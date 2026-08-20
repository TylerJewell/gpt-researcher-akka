package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.ScoredDoc;
import java.util.Comparator;
import java.util.List;

/**
 * The port's own stand-in for the source's LLM-based source ranking
 * ({@code gpt_researcher/skills/curator.py}) — sorts by score and keeps the top
 * {@code maxResults}. Deterministic and network-free.
 */
public class TopScoreCurator implements ReconcileCurator {

  @Override
  public List<ScoredDoc> curate(String query, List<ScoredDoc> docs, int maxResults) {
    List<ScoredDoc> sorted = docs.stream().sorted(Comparator.comparingDouble(ScoredDoc::score).reversed()).toList();
    return sorted.size() > maxResults ? sorted.subList(0, maxResults) : sorted;
  }
}
