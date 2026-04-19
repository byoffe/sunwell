---
name: experiment
description: Deploys an approved code change, runs a full profile + analyze cycle, computes the throughput and allocation-rate delta vs. the baseline run, and records the result in experiments.json.
when_to_use: When the user asks to run an experiment, measure a delta, or validate an approved improvement against a baseline.
argument-hint: "[--config <app-path>] [run-id]"
allowed-tools: "Agent Read Write"
---

## Experiment

Deploys the code change applied by `/improve`, runs a full profile + analyze
cycle, computes the delta vs. the baseline run, and records the result in
`experiments.json`.

**Usage:** `/experiment [--config <app-path>] [run-id]`

- `--config <app-path>` — directory containing `sunwell.yml`; defaults to `.`.
  For the toy-app during development, pass `examples/toy-app`.
- `run-id` — optional; the improvement run to experiment against. Defaults to
  the most recent entry in `{results-dir}/experiments.json` where
  `improvement-status` is `"implemented"`.

---

### Steps

**1. Read context**

Parse `$ARGUMENTS`:
- `--config <app-path>` → use that directory; default to `.`
- remaining first non-flag token → run-id override

Derive: `results-dir = {app-path}/sunwell-results`

Read `{results-dir}/experiments.json`. Identify the **improvement run**: the named
`run-id`, or the most recent entry where `improvement-status: "implemented"`.

If no such entry exists, stop:
> "No implemented improvement found. Run `/improve` first."

Extract from the improvement run:
- `suggested-next-focus` — focus for this experiment's profile run
- `files-changed` — the source files that were modified

Identify the **baseline run**: the most recent entry before the improvement run
where `analysis-path` is non-null (i.e., an analyzed run with no parent
experiment). This is the run the delta will be measured against.

**2. Deploy**

Spawn an Agent with this prompt:
> "Read `.claude/skills/deploy/SKILL.md` and execute it with arguments
> `--config {app-path} --target {target}`. Report 'Deploy complete' on
> success or 'Deploy failed: {reason}' on failure."

If the agent reports failure, stop:
> "Experiment stopped at Deploy. {agent error}
> The source change remains in the working tree."

**3. Profile and Collect**

Spawn an Agent with this prompt:
> "Read `.claude/skills/profile/SKILL.md` and execute it with arguments
> `--config {app-path} --focus {suggested-next-focus}`. Report 'Profile
> complete' on success or 'Profile failed: {reason}' on failure."

If the agent reports failure, stop:
> "Experiment stopped at Profile. {agent error}"

**4. Capture run-id and record parent**

Read `{results-dir}/experiments.json`. The last entry is the one the profile agent
just wrote. Extract its `run-id` as `{experiment-run-id}`.

Update that entry: set `"parent-run-id": "{baseline-run-id}"`. Write back.

**5. Analyze**

Spawn an Agent with this prompt:
> "Read `.claude/skills/analyze/SKILL.md` and execute it with arguments
> `--config {app-path} {experiment-run-id}`. Report 'Analyze complete' on
> success or 'Analyze failed: {reason}' on failure."

If the agent reports failure, stop:
> "Experiment stopped at Analyze. {agent error}"

**6. Read analysis results**

Read `{results-dir}/experiments.json`. Extract from the experiment entry:
- `analysis-path`, `hypothesis`, `suggested-next-focus`

**7. Compute delta**

For each benchmark present in `{results-dir}/{experiment-run-id}/summaries/`:

*Throughput:*
- Experiment: read `{results-dir}/{experiment-run-id}/jmh-output.txt`. Find the
  line for this benchmark. Extract the `Score` value (ops/s).
- Baseline: read `{results-dir}/{baseline-run-id}/jmh-output.txt` if it exists.
  Extract the same benchmark's score. If the file is absent, set to `null`.
- Compute `change-pct` = `(experiment - baseline) / baseline * 100` where
  both values are non-null; otherwise `null`.

*Allocation rate:*
- Experiment: read `{results-dir}/{experiment-run-id}/summaries/{benchmark}/gc.txt`.
  Extract the `Allocation rate` line value (MB/s).
- Baseline: read `{results-dir}/{baseline-run-id}/summaries/{benchmark}/gc.txt` if
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

Write the `delta` field to the experiment entry in `{results-dir}/experiments.json`
using the Write tool — do not use Bash or a heredoc.

**8. Report**

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

Analysis: {results-dir}/{experiment-run-id}/analysis.md
Hypothesis: {hypothesis}
Suggested next focus: {suggested-next-focus}

Next: run /improve --config {app-path} to propose the next
change, or /profile to start a fresh baseline.
```

---

### Edge Cases

- No `improvement-status: "implemented"` entry → stop: "No implemented improvement found. Run `/improve` first."
- No prior analyzed run to use as baseline → report delta as all-null; note "no baseline run available"
- Deploy agent reports failure → stop; source change remains in working tree
- `jmh-output.txt` absent on baseline run → throughput baseline is `null`; report allocation-rate delta only
- GC summary absent for a benchmark → allocation rate for that side is `null`
- Benchmark in experiment but not baseline → include in delta with `null` baseline values; note in report
