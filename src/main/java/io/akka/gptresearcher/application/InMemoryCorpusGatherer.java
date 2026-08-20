package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.SourceDoc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The port's own stand-in search+scrape source (SPEC-001 §1) — an in-memory corpus, keyed by
 * whichever of its documents share a word with the query. Deterministic and network-free, so
 * the HTTP surface is reachable and testable without any external retriever credentials.
 */
public class InMemoryCorpusGatherer implements SearchGatherer {

  private final List<SourceDoc> corpus;

  public InMemoryCorpusGatherer(List<SourceDoc> corpus) {
    this.corpus = corpus;
  }

  @Override
  public List<SourceDoc> search(String query) {
    Set<String> queryWords = wordsOf(query);
    List<SourceDoc> hits = new ArrayList<>();
    for (SourceDoc doc : corpus) {
      Set<String> docWords = wordsOf(doc.title() + " " + doc.content());
      if (!intersects(queryWords, docWords)) {
        continue;
      }
      hits.add(doc);
    }
    return hits;
  }

  private static boolean intersects(Set<String> a, Set<String> b) {
    for (String w : a) {
      if (b.contains(w)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> wordsOf(String text) {
    return new HashSet<>(Arrays.asList(text.toLowerCase(Locale.ROOT).split("\\W+")));
  }
}
