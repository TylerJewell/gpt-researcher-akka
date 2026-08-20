package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.SourceDoc;

/**
 * Stand-in for the source's embedding similarity search
 * ({@code gpt_researcher/context/compression.py:ContextCompressor}). Returns a similarity
 * score in [0, 1]; SPEC-001 rule 4 governs what the orchestrator does with it, not how the
 * embedding model computes it.
 */
public interface Scorer {
  double score(String query, SourceDoc doc);
}
