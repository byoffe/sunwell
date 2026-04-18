---
name: loop
description: Orchestrates the full Sunwell loop — deploy, profile, analyze, improve, experiment — resuming from the last known state in experiments.json. Pauses at the Improve gate for developer approval.
when_to_use: When the user asks to run the full loop, start an autonomous tuning session, or drive all stages end-to-end.
argument-hint: "[--config <app-path>] [--target <name>] [--focus <focus>]"
context: fork
agent: general-purpose
allowed-tools: "Agent Read Write Edit"
---

# Sunwell Loop

You are running the Sunwell performance engineering loop. You orchestrate
all stages — deploy, profile, collect, analyze, improve, experiment — and
drive forward automatically, pausing only for the developer approval gate
in the Improve stage.

**Usage:** `/loop [--config <app-path>] [--target <name>] [--focus <focus>]`

- `--config <app-path>` — directory containing `sunwell.yml` (default: `.`;
  for toy-app during development, pass `examples/toy-app`)
- `--target <name>` — override the `default-target` in `sunwell.yml`
- `--focus <focus>` — override the `default-focus` for the baseline run only

---

## Setup

**1. Parse arguments**

Parse from `$ARGUMENTS`:
- `--config <app-path>` → use that directory; default to `.`
- `--target <name>` → target override
- `--focus <focus>` → focus override

**2. Read `sunwell.yml`**

Read `{app-path}/sunwell.yml`. Resolve:
- `target`: `--target` arg → `default-target` field → stop with error
- `focus`: `--focus` arg → `default-focus` field → `baseline`

Read termination config (defaults apply if the `loop:` block is absent):
- `improvement-threshold-pct`: default `10`
- `stall-iterations`: default `3`

**3. Detect state**

Read `results/experiments.json` if it exists. If absent or empty, state is
`BASELINE`.

Inspect the **last entry** in the array and map it to a resume state:

| Last entry condition | State |
|---|---|
| No file, or empty array | `BASELINE` |
| `analysis-path: null` | `ANALYZE` |
| `analysis-path` set, `improvement-status: null` | `IMPROVE_PROPOSE` |
| `improvement-status: "proposed"` | `IMPROVE_PROPOSE` |
| `improvement-status: "approved"` | `IMPROVE_IMPLEMENT` |
| `improvement-status: "implemented"`, `delta: null` | `EXPERIMENT` |
| `delta` non-null | Run termination check; if continue → `IMPROVE_PROPOSE` |

**4. Determine iteration counter**

Count entries in `experiments.json` where `parent-run-id` is non-null (these
are experiment runs, one per iteration). Iteration N = count + 1 for the
current iteration.

**5. Report**

```
Sunwell Loop
─────────────────────────────────────────────
Config:    {app-path}/sunwell.yml
Target:    {target}
State:     {state}
Iteration: {N}
```

---

## BASELINE State

Run the initial profiling cycle. This establishes the reference point for
all subsequent experiment deltas.

**[ITERATION 1] [STAGE 1] Deploy**

Spawn an Agent with this prompt:
> "Read `.claude/skills/deploy/SKILL.md` and execute it with arguments
> `--config {app-path} --target {target}`. Report 'Deploy complete' on
> success or 'Deploy failed: {reason}' on failure."

If the agent reports failure, stop:
> "Loop stopped at Deploy. Fix the deploy error and re-invoke `/loop`
> to resume — the loop will re-enter at Deploy."

**[ITERATION 1] [STAGE 2] Profile + Collect**

Spawn an Agent with this prompt:
> "Read `.claude/skills/profile/SKILL.md` and execute it with arguments
> `--config {app-path} --focus {focus}`. Report 'Profile complete' on
> success or 'Profile failed: {reason}' on failure."

If the agent reports failure, stop:
> "Loop stopped at Profile. Fix the error and re-invoke to resume — the loop
> will re-enter at Profile."

**[ITERATION 1] [STAGE 3] Analyze**

Spawn an Agent with this prompt:
> "Read `.claude/skills/analyze/SKILL.md` and execute it with arguments
> `--config {app-path}`. Report 'Analyze complete' on success or
> 'Analyze failed: {reason}' on failure."

If the agent reports failure, stop:
> "Loop stopped at Analyze. Fix the error and re-invoke to resume — the loop
> will re-enter at Analyze."

Advance to `IMPROVE_PROPOSE`.

---

## ANALYZE State

A previous run collected successfully but analyze did not complete.

**[ITERATION N] [STAGE 3] Analyze — resuming**

Spawn an Agent with this prompt:
> "Read `.claude/skills/analyze/SKILL.md` and execute it for the most recent
> run-id in `results/experiments.json` with arguments `--config {app-path}`.
> Report 'Analyze complete' on success or 'Analyze failed: {reason}' on failure."

If the agent reports failure, stop:
> "Loop stopped at Analyze. Fix the error and re-invoke to resume."

Advance to `IMPROVE_PROPOSE`.

---

## IMPROVE_PROPOSE State

**[ITERATION N] [STAGE 4] Improve — proposing**

Read and follow `.claude/skills/improve/SKILL.md` **Phase 1 only**, passing
`--config {app-path}`.

The improve skill will write `proposal.md`, update `experiments.json`, present
the proposal, and **stop** waiting for developer input.

When the developer responds:

**On `approve` or `approve --focus <override>`:**
Advance to `IMPROVE_IMPLEMENT`, carrying the focus override if present.

**On `reject`:**
Read `results/experiments.json`. Find the last entry. Set
`improvement-status` → `"rejected"`. Write back.

Report:
> "Loop stopped. Last proposal rejected. Re-invoke `/sunwell:loop` to
> generate an alternative proposal from the same analysis, or run
> `/sunwell:improve --config {app-path}` manually."

Stop.

---

## IMPROVE_IMPLEMENT State

**[ITERATION N] [STAGE 4] Improve — implementing**

Read and follow `.claude/skills/improve/SKILL.md` **Phase 2 only**, using the
`approve` (or `approve --focus <override>`) response already received.

The improve skill will apply the diff and update `experiments.json` with
`files-changed` and `improvement-status: "implemented"`.

Advance to `EXPERIMENT`.

---

## EXPERIMENT State

**[ITERATION N] [STAGE 5] Experiment**

Read and follow `.claude/skills/experiment/SKILL.md` in full, passing
`--config {app-path}`.

The experiment skill deploys, profiles, analyzes, computes delta, and updates
`experiments.json` with the new entry (including `parent-run-id` and `delta`).

After the experiment skill completes, run the **Termination Check**.

---

## Termination Check

Read the `delta` field from the most recent entry in `experiments.json`.

**Success check:**

For each benchmark in `delta.metrics`, check:
- `allocation-rate-mb-s.change-pct` — if non-null and `change-pct ≤ -{threshold}%`
  (i.e., allocation rate dropped by at least threshold pct) → SUCCESS
- `throughput-ops-s.change-pct` — if non-null and `change-pct ≥ +{threshold}%`
  (i.e., throughput increased by at least threshold pct) → SUCCESS

If ANY benchmark meets either condition → **go to Final Report (SUCCESS)**.

**Stall check:**

Look at the last `{stall-iterations}` entries in `experiments.json` that
have a non-null `delta` field. If there are fewer than `stall-iterations`
such entries, check all available ones.

For each such entry, a benchmark is "not improving" if both:
- `allocation-rate-mb-s.change-pct` is null or `|change-pct| < 2`
- `throughput-ops-s.change-pct` is null or `|change-pct| < 2`

If ALL benchmarks are "not improving" across ALL checked entries → **go to
Final Report (STALL)**.

Note in the stall report if fewer than `stall-iterations` entries were
available: "Stall detected across {N} of {stall-iterations} iterations."

**Continue:**

If neither condition is met, increment the iteration counter and advance to
`IMPROVE_PROPOSE` for the next iteration.

```
[ITERATION {N+1}] — continuing loop
```

---

## Final Report

**On SUCCESS:**

```
Sunwell Loop Complete — SUCCESS
─────────────────────────────────────────────
Target improvement of {threshold}% reached.
Iterations: {N}

Cumulative delta ({first-baseline-run-id} → {last-experiment-run-id}):
  {BenchmarkName}
    Allocation rate: {first-baseline} → {last-experiment} MB/s  ({total-change-pct}%)
    Throughput:      {first-baseline} → {last-experiment} ops/s  ({total-change-pct}%)
    (n/a where metric unavailable for either end)

Experiment tree: results/experiments.json
```

**On STALL:**

```
Sunwell Loop Complete — STALL
─────────────────────────────────────────────
No improvement detected in last {N} iteration(s).
Consider: a fresh baseline profile, a different focus, or a manual
investigation of the remaining hotspots.

Cumulative delta ({first-baseline-run-id} → {last-experiment-run-id}):
  {BenchmarkName}
    Allocation rate: {first-baseline} → {last-experiment} MB/s  ({total-change-pct}%)
    Throughput:      {first-baseline} → {last-experiment} ops/s  ({total-change-pct}%)

Experiment tree: results/experiments.json
```

**Cumulative delta computation:**

- First baseline: the first entry in `experiments.json` that has
  `analysis-path` set and `parent-run-id: null` (the original baseline run)
- Last experiment: the most recent entry with a non-null `delta`
- For each benchmark, read allocation rate from each run's
  `summaries/{benchmark}/gc.txt` and throughput from `jmh-output.txt`
  (null if absent). Compute overall `change-pct` the same way as the
  per-experiment delta.

---

## Edge Cases

- `experiments.json` missing and `--focus <non-baseline>` given → warn:
  "First run should use baseline focus. Continuing with `{focus}` as specified."
  Proceed.
- `improvement-status: "rejected"` on last entry → state is `IMPROVE_PROPOSE`;
  the improve skill will see `improvement-status: "rejected"` and generate a
  new proposal from the same analysis (not re-present the old one).
- Deploy fails during Experiment stage → stop; source change remains in working
  tree; re-invoking loop re-enters at `EXPERIMENT` and retries deploy.
- Analyze fails (no JFR files found) → stop; loop re-enters at `ANALYZE` on
  re-invoke.
- Stall check with fewer than `stall-iterations` experiment entries → check all
  available; note count in report.