package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.SourceDoc;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The port's own stand-in for the source's embedding similarity search — overlap coefficient
 * (intersection over the smaller word set, so a short query against a long document is not
 * penalized for the document's own length) between query words and document words, in [0, 1].
 * Deterministic and network-free.
 */
public class WordOverlapScorer implements Scorer {

  @Override
  public double score(String query, SourceDoc doc) {
    Set<String> queryWords = wordsOf(query);
    Set<String> docWords = wordsOf(doc.title() + " " + doc.content());
    if (queryWords.isEmpty() || docWords.isEmpty()) {
      return 0.0;
    }
    Set<String> intersection = new HashSet<>(queryWords);
    intersection.retainAll(docWords);
    int smaller = Math.min(queryWords.size(), docWords.size());
    return (double) intersection.size() / smaller;
  }

  private static Set<String> wordsOf(String text) {
    return new HashSet<>(Arrays.asList(text.toLowerCase(Locale.ROOT).split("\\W+")));
  }
}
