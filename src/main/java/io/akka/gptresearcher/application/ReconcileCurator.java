package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.ScoredDoc;
import java.util.List;

/**
 * Stand-in for the source's LLM-based source ranking ({@code gpt_researcher/skills/curator.py}).
 * SPEC-001 rule 6: opt-in, and the orchestrator falls back to the un-curated list if this
 * throws — the fallback is the orchestrator's job, not the curator implementation's.
 */
public interface ReconcileCurator {
  List<ScoredDoc> curate(String query, List<ScoredDoc> docs, int maxResults);
}
