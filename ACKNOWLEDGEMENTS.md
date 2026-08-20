# Acknowledgements

This project is a port of **[assafelovic/gpt-researcher](https://github.com/assafelovic/gpt-researcher)**,
read and run at commit `5d84d2f` (2026-08-20).

## Licence

gpt-researcher is **Apache License 2.0**, © Assaf Elovic and contributors. A copy of that
licence is included as `LICENSE-gpt-researcher`, which Apache-2.0 requires of any work
carrying its material, along with the notice of what was changed that section 4(b) asks
for — this whole file is that notice.

## What was copied

**No source was copied.** No Python file, fragment or expression from gpt-researcher
appears here; every file in `src/` was written for this project, against
`specs/SPEC-001-gpt-researcher.md` in the harness repository (`TylerJewell/specify`).

Three things were taken across deliberately, and none of them is source:

- **The behaviour itself.** That planning runs once and its output plus the original query
  is what fans out; that fan-out is concurrent and one sub-query's result does not gate
  another's; that a shared visited-URL set dedupes across concurrently gathered sub-queries;
  that a result below the similarity threshold is dropped and the rest capped per sub-query;
  that an empty sub-query result contributes nothing to the join; that source curation is
  opt-in and falls back to the un-curated context on failure; and that the whole pipeline is
  a single pass, not a loop — all derived from `gpt_researcher/skills/researcher.py`,
  `gpt_researcher/skills/curator.py` and `gpt_researcher/actions/query_processing.py`, and
  reproduced deliberately. That is what a port is.
- **Two default configuration values, reproduced as data.** `SIMILARITY_THRESHOLD = 0.42`
  and the per-sub-query result cap of 10 are the source's own defaults
  (`gpt_researcher/config/variables/default.py`), not independently chosen.
- **Probe and scenario inputs.** `gpt-researcher-port/probes/probe_01_dedup.py` and
  `gpt-researcher-port/bench/scenarios.json` in the harness repository reproduce two of the
  source's own functions verbatim (`_get_new_urls`, and the gather/filter/join step of
  `_get_context_by_web_search`) to check the claims those functions make by running them;
  they are cited by file and line in `docs/question-log.md` and are not part of this
  project's own build.

## What that means for this project's licence

Apache-2.0 is permissive and imposes no share-alike obligation on works derived from its
behaviour rather than its text, so nothing about the original constrains what this project
may be licensed as. Its attribution and notice requirements apply to redistributed copies
of its own source, and none is included here beyond the `LICENSE-gpt-researcher` file
itself and this notice.

## Also used

- **[Akka](https://akka.io)** — the SDK and runtime this port is built on
  (`akka-javasdk` 3.6.3, Business Source License 1.1).
