package io.akka.gptresearcher.domain;

import java.util.List;

/**
 * SPEC-001 §4 open decision — the finished result of one orchestration run.
 *
 * <p>{@code text} is always a string, including the empty-result case, unlike the source's
 * bare {@code []} for that same case (question-log row 2). {@code sourceCount} is how many
 * distinct URLs contributed, after dedup and thresholding, before any curation.
 */
public record ResearchContext(String text, int sourceCount, boolean curated, List<String> subQueries) {

  public static ResearchContext empty(List<String> subQueries) {
    return new ResearchContext("", 0, false, subQueries);
  }
}
