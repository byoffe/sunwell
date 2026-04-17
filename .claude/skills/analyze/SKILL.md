---
name: analyze
description: Read JFR recordings from results/<run-id>/, run Java summarization scripts per benchmark and analysis dimension, spawn one subagent per benchmark to interpret findings, reduce into analysis.md, and update experiments.json.
allowed-tools: "Agent Bash Read Write"
---

## Analyze

Reads JFR recordings from a profiling run, reduces them to compact summaries
via local Java scripts, and produces a plain-language `analysis.md` with a
hypothesis and suggested next focus.

**Usage:** `/analyze [run-id]`

- `run-id` — optional; defaults to the most recent entry in `results/experiments.json`

---

### Steps

**1. Read context**

Read `results/experiments.json`. Identify the target run: the named `run-id`
if provided, otherwise the most recent entry (last in the array). Extract:
- `run-id`, `focus`, `target`

Read `examples/toy-app/sunwell.yml`. Extract `analyze.hints` if present:
- `thread` — thread name pattern (substring match)
- `package` — package prefix for stack frame filtering

**2. Discover recordings**

Glob `results/{run-id}/**/profile.jfr`. Each file is one benchmark.
Extract a short benchmark name from the directory path — the portion between
`{run-id}/` and the final `/profile.jfr` (e.g.,
`dev.sunwell.toy.CpuHogBenchmark.deduplicateTags-Throughput`).

If no `.jfr` files are found, stop: "No recordings found under
`results/{run-id}/`. Run `/profile` first."

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
`results/{run-id}/summaries/{benchmark-short-name}/{dimension}.txt`

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
`results/{run-id}/analysis.md` using this structure:

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
analysis — the summaries stay in `results/{run-id}/summaries/`.

**7. Update experiments.json**

Read `results/experiments.json`. Find the entry for this `run-id`. Set:
- `analysis-path` → `"results/{run-id}/analysis.md"`
- `suggested-next-focus` → the focus name from the Suggested Next Focus section
- `hypothesis` → first sentence of the Hypothesis section

Write the updated JSON back.

**8. Report**

Confirm:
- Run-id, focus, number of benchmarks analyzed
- Summary files written to `results/{run-id}/summaries/`
- `analysis.md` path
- Suggested next focus
- Ready for improve stage
