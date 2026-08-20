package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.SourceDoc;
import java.util.List;

/**
 * Stand-in for a retriever+scraper pair — "search a query, scrape what comes back" is one
 * external call from the orchestration's point of view. Out of scope per SPEC-001 §1: the
 * source has 15+ retriever backends and 6+ scraper backends behind this same seam.
 */
public interface SearchGatherer {
  List<SourceDoc> search(String query);
}
