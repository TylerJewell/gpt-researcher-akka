package io.akka.gptresearcher.domain;

/** SPEC-001 rule 4. A document kept for its sub-query only once its score clears the threshold. */
public record ScoredDoc(SourceDoc doc, double score) {}
