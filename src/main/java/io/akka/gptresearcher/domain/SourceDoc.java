package io.akka.gptresearcher.domain;

/** One retrieved document, already scraped — url, title and the text content. */
public record SourceDoc(String url, String title, String content) {}
