package io.akka.gptresearcher.application;

import io.akka.gptresearcher.domain.SourceDoc;
import java.util.List;

/** A small fixed corpus so the HTTP endpoint is reachable and demonstrable without any external retriever. */
public final class SeedCorpus {

  private SeedCorpus() {}

  public static List<SourceDoc> docs() {
    return List.of(
        new SourceDoc(
            "https://example.org/akka-overview",
            "Akka Agentic Platform Overview",
            "Akka is a platform for building reliable AI agents and event-driven microservices with a distributed runtime."),
        new SourceDoc(
            "https://example.org/akka-entities",
            "Akka Event Sourced Entities",
            "Event sourced entities in Akka keep durable state as an append-only event journal with snapshots."),
        new SourceDoc(
            "https://example.org/akka-workflows",
            "Akka Workflows",
            "Workflows in Akka orchestrate multi-step processes with durable execution and automatic retries."),
        new SourceDoc(
            "https://example.org/research-orchestration",
            "Research Orchestration Patterns",
            "Research orchestration plans sub-queries, fans them out concurrently, gathers and dedupes results, then reconciles sources."),
        new SourceDoc(
            "https://example.org/gpt-researcher-history",
            "GPT Researcher Project History",
            "GPT Researcher is an open source autonomous research agent that plans, searches and writes reports."),
        new SourceDoc(
            "https://example.org/similarity-search",
            "Similarity Search for Context Gathering",
            "Similarity search scores documents against a query and keeps only those above a threshold."));
  }
}
