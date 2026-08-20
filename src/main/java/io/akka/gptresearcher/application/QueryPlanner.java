package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.SourceDoc;
import java.util.List;

/**
 * Stand-in for the source's strategic-LLM sub-query generation
 * ({@code gpt_researcher/actions/query_processing.py:generate_sub_queries}). The network call
 * and the model behind it are a fair stand-in under PIPELINE.md; the orchestration rule this
 * spec governs is what happens to the planner's output, not how the LLM produces it.
 */
public interface QueryPlanner {
  List<String> plan(String query, List<SourceDoc> initialResults);
}
