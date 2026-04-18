---
name: analyze
description: Reduces JFR recordings to per-benchmark summaries via Java scripts, interprets findings with per-benchmark subagents, and writes analysis.md with a hypothesis and suggested next focus.
when_to_use: When the user asks to analyze a profiling run or generate analysis.md from collected JFR recordings.
argument-hint: "[--config <app-path>] [run-id]"
allowed-tools: "Agent Bash(java .claude/skills/analyze/summarize-cpu.java) Bash(java .claude/skills/analyze/summarize-alloc.java) Bash(java .claude/skills/analyze/summarize-gc.java) Read Write"
---

## Analyze

Reads JFR recordings from a profiling run, reduces them to compact summaries
via local Java scripts, and produces a plain-language `analysis.md` with a
hypothesis and suggested next focus.

**Usage:** `/analyze [--config <app-path>] [run-id]`

- `--config <app-path>` — directory containing `sunwell.yml`; defaults to `.`
  (current working directory). For the toy-app during development, pass
  `examples/toy-app`.
- `run-id` — optional; defaults to the most recent entry in `{results-dir}/experiments.json`

---

### Steps

**1. Read context**

Parse `$ARGUMENTS`:
- `--config <app-path>` → use that directory; default to `.`
- remaining first non-flag token → run-id override

Derive: `results-dir = {app-path}/sunwell-results`

Read `{results-dir}/experiments.json`. Identify the target run: the named
`run-id` if provided, otherwise the most recent entry (last in the array).
Extract:
- `run-id`, `focus`, `target`

Read `{app-path}/sunwell.yml`. Extract `analyze.hints` if present:
- `thread` — thread name pattern (substring match)
- `package` — package prefix for stack frame filtering

**2. Discover recordings**

Glob `{results-dir}/{run-id}/**/profile.jfr`. Each file is one benchmark.
Extract a short benchmark name from the directory path — the portion between
`{run-id}/` and the final `/profile.jfr` (e.g.,
`dev.sunwell.toy.CpuHogBenchmark.deduplicateTags-Throughput`).

If no `.jfr` files are found, stop: "No recordings found under
`{results-dir}/{run-id}/`. Run `/profile` first."

**3. Determine active dimensions**

Based on `focus`, select which scripts to run:

| Focus     | CPU | Allocation | GC  |
|-----------|-----|------------|-----|
| `baseline`| yes | yes        | yes |
| `gc`      | no  | yes        | yes |
| `cpu`     | yes | no         | no  |
| `memory`  | no  | yes        | yes |
| `lock`    | no  | no         | no  |

*(lock focus has no JFR-backed script yet — note in analysis that lock data
is unavailable until async-profiler is integrated)*

**4. Run summarization scripts**

For each benchmark × active-dimension, run the appropriate script.
Build the script path relative to the repo root:
`.claude/skills/analyze/summarize-{dimension}.java`

Construct the command with hints resolved from `sunwell.yml`:

```
java .claude/skills/analyze/summarize-cpu.java {jfr-path} [--thread {thread}] [--package {package}]
java .claude/skills/analyze/summarize-alloc.java {jfr-path} [--thread {thread}] [--package {package}]
java .claude/skills/analyze/summarize-gc.java {jfr-path}
```

Write each script's output to:
`{results-dir}/{run-id}/summaries/{benchmark-short-name}/{dimension}.txt`

Create the summaries directory if it does not exist. Scripts for different
benchmarks may run in parallel; scripts for the same benchmark run in parallel
across dimensions.

If a script exits non-zero, write the error to the summary file and continue —
do not halt the full analysis for one failing dimension.

**5. Spawn subagents — one per benchmark**

For each benchmark, spawn an Agent with:
- The benchmark's short name and JFR file path
- The focus and any hints applied
- Paths to all dimension summary files for this benchmark
- This instruction:

> "Read each summary file listed below. Produce a structured findings block
> for this benchmark covering the active dimensions. For each dimension:
> identify the top hotspots or patterns, note anything anomalous, and assess
> severity (high / medium / low). Return plain text, under 300 words total.
> Do not fabricate data not present in the summaries."

Collect all subagent findings.

**6. Reduce into analysis.md**

Read all subagent findings (small structured text). Synthesize into
`{results-dir}/{run-id}/analysis.md` using this structure:

```markdown
# Analysis: {run-id}

**Focus:** {focus} | **Target:** {target} | **Benchmarks:** {N}
**Hints applied:** thread={thread}, package={package}
(omit hints line if no hints were set)

## {BenchmarkName}

### CPU Hotspots
(omit section if CPU not active for this focus)

### Allocation
(omit section if allocation not active for this focus)

### GC Behavior
(omit section if GC not active for this focus)

## Cross-Benchmark Observations
(omit if only one benchmark)

## Hypothesis
One paragraph. What is the primary bottleneck and why does the data support
that conclusion? Be specific — cite methods, line numbers, or metrics.

## Suggested Next Focus
{focus-name} — one sentence explaining why this focus is the right next step.
```

Write the file. Plain language throughout. No raw profiler output in the
analysis — the summaries stay in `{results-dir}/{run-id}/summaries/`.

**7. Update experiments.json**

Read `{results-dir}/experiments.json`. Find the entry for this `run-id`. Set:
- `analysis-path` → `"{results-dir}/{run-id}/analysis.md"`
- `suggested-next-focus` → the focus name from the Suggested Next Focus section
- `hypothesis` → first sentence of the Hypothesis section

Write the updated JSON back.

**8. Report**

Confirm:
- Run-id, focus, number of benchmarks analyzed
- Summary files written to `{results-dir}/{run-id}/summaries/`
- `analysis.md` path
- Suggested next focus
- Ready for improve stage
