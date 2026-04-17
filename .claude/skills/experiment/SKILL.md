---
name: experiment
description: Deploy the current working tree (improvement already applied by /improve), profile with the suggested focus, analyze, compute delta vs. the baseline run, and record everything in a new experiments.json entry.
allowed-tools: "Agent Bash Read Write"
---

## Experiment

Deploys the code change applied by `/sunwell:improve`, runs a full
profile + analyze cycle, computes the delta vs. the baseline run, and
records the result in `experiments.json`.

**Usage:** `/experiment [--config <app-path>] [run-id]`

- `--config <app-path>` — directory containing `sunwell.yml`; defaults to `.`.
  For the toy-app during development, pass `examples/toy-app`.
- `run-id` — optional; the improvement run to experiment against. Defaults to
  the most recent entry in `results/experiments.json` where
  `improvement-status` is `"implemented"`.

---

### Steps

**1. Read context**

Parse `$ARGUMENTS`:
- `--config <app-path>` → use that directory; default to `.`
- remaining first non-flag token → run-id override

Read `results/experiments.json`. Identify the **improvement run**: the named
`run-id`, or the most recent entry where `improvement-status: "implemented"`.

If no such entry exists, stop:
> "No implemented improvement found. Run `/sunwell:improve` first."

Extract from the improvement run:
- `suggested-next-focus` — focus for this experiment's profile run
- `files-changed` — the source files that were modified

Identify the **baseline run**: the most recent entry before the improvement run
where `analysis-path` is non-null (i.e., an analyzed run with no parent
experiment). This is the run the delta will be measured against.

Read `{app-path}/sunwell.yml`. Extract target config (host, port, user, key,
remote-path, jar).

**2. Deploy**

The improvement is already applied to the working tree. Build and deploy it:

```!
bash .claude/skills/deploy/deploy-ssh.sh \
  {host} {port} {user} {key} {jar} {remote-path}
```

If deploy fails, stop and report. The source change remains in the working tree.

**3. Generate run-id**

```!
date -u +%Y%m%d-%H%M%S
```

Use the output as `<experiment-run-id>`.

**4. Profile**

```!
bash .claude/skills/profile/profile-jfr.sh \
  {host} {port} {user} {key} {remote-path} {jar-filename} {experiment-run-id}
```

JMH stdout is captured to `/tmp/{experiment-run-id}/jmh-output.txt` on the
remote. If the script exits non-zero, stop and report.

**5. Collect**

```!
bash .claude/skills/profile/collect-ssh.sh \
  {host} {port} {user} {key} /tmp/{experiment-run-id} results/{experiment-run-id}
```

Copies all JFR recordings and `jmh-output.txt` to `results/{experiment-run-id}/`.
If collect fails, stop and report.

**6. Write experiments.json entry**

Append a new entry for this experiment run:

```json
{
  "run-id": "<experiment-run-id>",
  "timestamp": "<ISO-8601 UTC>",
  "target": "<target-name>",
  "focus": "<suggested-next-focus>",
  "profiler": "jfr",
  "artifact-path": "results/<experiment-run-id>/",
  "analysis-path": null,
  "hypothesis": null,
  "suggested-next-focus": null,
  "files-changed": [],
  "delta": null,
  "proposal-path": null,
  "improvement-status": null,
  "parent-run-id": "<baseline-run-id>"
}
```

Write back to `results/experiments.json`.

**7. Analyze**

Run the full analyze skill playbook (same steps as `/sunwell:analyze`) for
the experiment run:

- Read `{app-path}/sunwell.yml` for `analyze.hints`
- Glob `results/{experiment-run-id}/**/profile.jfr` to discover benchmarks
- Determine active dimensions from the focus table
- Run summarization scripts per benchmark × dimension; write to
  `results/{experiment-run-id}/summaries/`
- Spawn one subagent per benchmark to interpret its summaries
- Reduce into `results/{experiment-run-id}/analysis.md`
- Update the experiment entry in `experiments.json`:
  - `analysis-path` → `"results/{experiment-run-id}/analysis.md"`
  - `hypothesis` → first sentence of the Hypothesis section
  - `suggested-next-focus` → focus from the Suggested Next Focus section

**8. Compute delta**

For each benchmark discovered in step 7:

*Throughput:*
- Experiment: read `results/{experiment-run-id}/jmh-output.txt`. Find the
  line for this benchmark (match on benchmark short name). Extract the
  `Score` value (ops/s).
- Baseline: read `results/{baseline-run-id}/jmh-output.txt` if it exists.
  Extract the same benchmark's score. If the file is absent, set baseline
  throughput to `null`.
- Compute `change-pct` = `(experiment - baseline) / baseline * 100` where
  both values are non-null; otherwise `null`.

*Allocation rate:*
- Experiment: read `results/{experiment-run-id}/summaries/{benchmark}/gc.txt`.
  Extract the `Allocation rate` line value (MB/s).
- Baseline: read `results/{baseline-run-id}/summaries/{benchmark}/gc.txt` if
  it exists. Extract the same value. If absent, set to `null`.
- Compute `change-pct` the same way.

Build the `delta` object:

```json
{
  "baseline-run-id": "<baseline-run-id>",
  "metrics": [
    {
      "benchmark": "<benchmark-short-name>",
      "throughput-ops-s": {
        "baseline": <number or null>,
        "experiment": <number or null>,
        "change-pct": <number or null>
      },
      "allocation-rate-mb-s": {
        "baseline": <number or null>,
        "experiment": <number or null>,
        "change-pct": <number or null>
      }
    }
  ]
}
```

Write the `delta` field to the experiment entry in `results/experiments.json`.

**9. Report**

```
Experiment Complete
─────────────────────────────────────────────
Run:      {experiment-run-id}
Focus:    {focus}
Parent:   {baseline-run-id}
Change:   {files-changed from improvement run}

Delta per benchmark:
  {BenchmarkName}
    Throughput:      {baseline} → {experiment} ops/s  ({change-pct}%)
    Allocation rate: {baseline} → {experiment} MB/s   ({change-pct}%)
    (null values shown as "n/a")

Analysis: results/{experiment-run-id}/analysis.md
Hypothesis: {hypothesis}
Suggested next focus: {suggested-next-focus}

Next: run /sunwell:improve --config {app-path} to propose the next
change, or /sunwell:profile to start a fresh baseline.
```

---

### Edge Cases

- No `improvement-status: "implemented"` entry → stop: "No implemented improvement found. Run `/sunwell:improve` first."
- No prior analyzed run to use as baseline → report delta as all-null; note "no baseline run available"
- Deploy fails → stop; source change remains in working tree
- `jmh-output.txt` absent on baseline run → throughput baseline is `null`; report allocation-rate delta only
- GC summary absent for a benchmark → allocation rate for that side is `null`
- Benchmark in experiment but not baseline → include in delta with `null` baseline values; note in report
