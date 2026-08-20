# gpt-researcher-akka

Plans a research query into several smaller questions, answers each one at the same time,
throws out what does not clear a relevance bar, and returns one finished answer.

A port of [assafelovic/gpt-researcher](https://github.com/assafelovic/gpt-researcher) onto
**Akka**, built with **Akka Specify**.

---

## Where it came from

assafelovic/gpt-researcher is an autonomous research agent: given a question, it plans a
set of smaller questions, searches and scrapes the web for each one, and writes a report
from what it finds. It was ported to derive a specification format precise enough to
regenerate a system on a different stack — the port is the vehicle, the specification is
the deliverable.

Only one slice of gpt-researcher is rebuilt here: planning the smaller questions, running
them at the same time, gathering and de-duplicating what comes back, optionally ranking
the combined sources, and stopping — not the fifteen-plus web search providers, the
scraping backends, the report-writing step, or the separate iterative deep-research mode.
The specifications the port was generated from are in
[TylerJewell/specify](https://github.com/TylerJewell/specify) under `gpt-researcher-port/`.

---

## assafelovic/gpt-researcher → this port

📉 591 Python lines (scope-matched) → **289 Java lines**<br>
📁 4 files (scope-matched) → **15 files**<br>
🧪 0 dedicated tests found in the source for this slice → **17 tests**<br>
🔁 0 rules broken on purpose → **4 of 4 rules broken and caught**<br>
🎯 5 of 5 shared scenarios give the same answer<br>
⚡ 0.0409 ms/op (Python, in-process) → **0.0756 ms/op** (Java, in-process)

Full method and the numbers that did *not* make this list: [`bench/REPORT.md`](https://github.com/TylerJewell/specify/blob/main/gpt-researcher-port/bench/REPORT.md).

---

## What it took to build

⏱️ **0.5 hours** from the first command to the published repository, **0.5** of them active<br>
💬 **326** exchanges with the model<br>
✍️ **154,970** tokens written by the model, **55,476,207** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **17** tests

```bash
python toolkit/tokens.py --port gpt-researcher    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/specify/tree/main/port-log).

---

## What it does

Given a question, this service:

1. Runs one initial search, then asks a planner for a handful of smaller, more specific
   questions to research alongside the original.
2. Researches every one of those questions at the same time, not one after another.
3. As each question's results come back, it throws out anything that has already been
   found by another question running at the same time — the same web page is only ever
   used once, no matter how many of the smaller questions turned it up — and it throws out
   anything that does not clear a relevance bar against that question.
4. Can optionally take one more pass over everything gathered and rank it down to the
   strongest handful of sources, if that pass fails for any reason the unranked results are
   used instead rather than losing the research.
5. Joins whatever survives into one answer and stops. It does not go back and plan again
   based on what it found — one pass, start to finish.

This service supplies its own small set of example pages to search over and its own
overlap-based way of judging relevance, so it can be tried out immediately. A real
deployment would connect it to an actual web search provider and an actual language model
for planning and relevance, without changing the part that plans, runs things at the same
time, gathers, and stops.

## Running it — the short path

The service listens on port 9026. With the Akka local runtime already running:

```bash
# from this directory
mvn compile
# then use the Akka tooling to run this service on the shared local runtime
```

Ask it to research something:

```bash
curl -X POST http://localhost:9026/research \
  -H "Content-Type: application/json" \
  -d '{"query": "akka workflows", "curateSources": false}'
```

The reply is the finished answer, how many sources it drew from, whether the optional
ranking pass ran, and the list of smaller questions it researched.

## Design decisions

**Running things at the same time, searching, judging relevance, and ranking sources are
each a separate, swappable piece.** The source has more than fifteen web search providers,
six scraping backends, and a language model behind planning, relevance and ranking. None
of those are rebuilt here — see `Where it differs`, below — so this port keeps the seam
between "run several questions at once and combine what comes back" and "how one question
turns into results" exactly where the source draws it, and supplies its own example
implementations of the second half so the service works out of the box.

**The shared list of already-used web pages is checked and claimed as one step, not two.**
Even though several questions are being researched at the same time, checking "has this
page been used yet" and marking it used have to happen together — otherwise two questions
running at the same moment could both think a page is unused and both claim it.

**An optional ranking pass falls back to the unranked results if it fails.** Ranking the
combined sources down to the strongest few is one more step that can go wrong, and a
research run that already gathered good results should not be thrown away because the
extra step on top of it failed.

**A blank question is rejected before anything runs.** An empty or missing question is a
caller mistake, not a research outcome, so it is reported as one immediately instead of
being researched into nothing.

**Every kept result is scored against a fixed relevance bar, not just sorted and
truncated.** A result that never clears the bar is dropped even if there is room left
under the per-question cap, so the cap and the relevance bar are two independent limits
rather than one standing in for the other.

## Where it differs from assafelovic/gpt-researcher

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **When every smaller question comes back with nothing, the source returns an empty
  list; this port returns an empty answer.** The source's own return type is only
  sometimes a list and only sometimes text, depending on whether anything was found — a
  typed response cannot vary its own shape that way, so this port always answers with
  text, empty or not.
- **A failure while researching one smaller question stops the whole request here; the
  source only records nothing for that question and keeps the rest.** The source wraps its
  whole gather step in a broad catch that turns any failure into "found nothing"; this
  port does not, so a failing example search provider surfaces as a failed request rather
  than a silently incomplete answer. Swapping in a production search provider that can
  itself fail without stopping the request would need this behaviour revisited.
- **The web search providers, the scraping backends, six kinds of document source, the
  report-writing step, cost tracking in real currency, and the separate iterative
  deep-research mode are not rebuilt.** They sit outside the slice this port covers — see
  `Where it came from`, above.
- **Optionally ranking the combined sources down to the strongest few is off by default
  here, the same as in the source**, and this port additionally guarantees that a failure
  during that optional pass falls back to the unranked results rather than losing the
  research — the source's own version does this too, but this port makes it a rule with a
  test rather than leaving it to the one code path that happens to implement it.
- **How relevant a page is to a question, and how a handful of smaller questions gets
  chosen from the first search, are each answered by this port's own example logic
  (overlapping words, and search-result titles), not by an embedding model or a language
  model.** The source uses both; this port's own versions are swappable, deterministic, and
  do not require any external service or credentials to try.
- **The relevance bar always applies here; in the source it has an exception.** When the
  source has few enough pages and little enough total text for one question, it skips
  relevance scoring entirely and uses whatever it found, unranked and unfiltered. This port
  always scores and always applies the bar, regardless of how much was found.

## Licence

assafelovic/gpt-researcher is Apache License 2.0, © Assaf Elovic and contributors. This
port reimplements the behaviour without copied source; see
[`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md).
