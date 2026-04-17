# Design: Sunwell — Full Loop

## Increment 1 — Configuration + Deploy + Profile + Collect

### Scope

Addresses these requirements acceptance criteria:
- All **Configuration** criteria
- All **Deploy** criteria
- All **Profile** criteria
- All **Collect** criteria

Deferred to later increments: Analyze, Improve, Experiment, Loop orchestration.

---

### Approach

Skills are the intelligent layer. Scripts are dumb transport. Each skill reads
`sunwell.yml` directly, resolves all config (target, focus, flags, paths), and
passes fully-resolved values as arguments to the appropriate transport script.
Scripts know nothing about `sunwell.yml`, focus, or app names — they receive
host, port, key, paths, and flags as positional arguments and execute one
mechanical operation.

This keeps scripts independently testable and transport-specific without
becoming god scripts. The skill is where the intelligence lives.

---

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Who reads `sunwell.yml`? | The skill (Claude), not the script | Scripts stay transport-only; no config parsing logic in bash |
| How does config reach the script? | Positional CLI args | Simple, no dependencies, explicit at the call site |
| One script per transport or per stage? | Per transport | `deploy-ssh.sh` and `collect-ssh.sh` are both SSH/SCP; same mechanism, not duplicated |
| Do stage skills read `sunwell.yml` independently? | Yes | Each skill is usable standalone without the loop; loop just orchestrates calls |
| run-id format | `YYYYMMDD-HHMMSS` | Sortable, human-readable, no dependencies |
| Where does `experiments.json` get initialized? | Collect stage, after recording lands | Collect is the first stage that produces a durable artifact |
| JFR output location on remote | `/tmp/<run-id>.jfr` | Ephemeral, avoids permissions issues; collect SCPs it home immediately |
| `deploy.sh` rename | `deploy-ssh.sh` | Name reflects the transport, not the stage; aligns with future `deploy-k8s.sh` etc. |

---

### Skill Granularity

A skill maps to a developer-visible action — something a developer would say
out loud or invoke directly. "Deploy" and "profile" are developer-visible.
"Collect" is an implementation detail of profiling; a developer would not
invoke it independently. Collect is therefore folded into the profile skill.

If a stage later grows complex enough to warrant standalone invocation, it
graduates to its own skill at that point. Not before.

### Script Co-location

Scripts are owned by their skill and live in the skill's directory. They are
an implementation detail of that skill, not a shared library. When skills
migrate from `.claude/skills/` to `skills/` for plugin publishing, scripts
move with them.

The top-level `scripts/` directory is retired. `deploy.sh` moves into the
deploy skill directory as `deploy-ssh.sh` via `git mv`.

### Script Interfaces

Scripts are called by skills with fully-resolved arguments. No config file
parsing. No environment variable magic.

**`.claude/skills/deploy/deploy-ssh.sh`** — build JAR locally, SCP to target, verify:
```
deploy-ssh.sh <host> <port> <user> <key> <local-jar> <remote-path>
```

**`.claude/skills/profile/profile-jfr.sh`** — SSH in, run JAR with JFR flags, wait:
```
profile-jfr.sh <host> <port> <user> <key> <remote-path> <jar-filename> <duration> <run-id>
```
Leaves recording at `/tmp/<run-id>.jfr` on the remote host.

**`.claude/skills/profile/collect-ssh.sh`** — SCP recording from remote to local results dir:
```
collect-ssh.sh <host> <port> <user> <key> <remote-file> <local-dir>
```
Produces `<local-dir>/recording.jfr`. Called by the profile skill immediately
after `profile-jfr.sh` completes — not a separate developer-invocable stage.

---

### Focus Resolution (Profile Skill)

The profile skill owns the defaults table. Resolution order:

1. Focus from CLI arg (`--focus cpu`) if provided
2. Otherwise `default-focus` from `sunwell.yml`
3. Otherwise `baseline`

Then:

4. Look up focus in built-in defaults table → profiler, duration, flags
5. Apply any `profile.overrides.<focus>` from `sunwell.yml` on top
6. Pass resolved values to the appropriate script (`profile-jfr.sh` for JFR;
   async-profiler script when added)

Built-in defaults (owned by the profile skill):

| Focus | Profiler | Duration | JVM Flags |
|---|---|---|---|
| `baseline` | JFR | 60s | `-XX:StartFlightRecording=duration=60s,filename=/tmp/<run-id>.jfr,settings=profile` |
| `cpu` | async-profiler | 30s | *(deferred — async-profiler not yet delivered)* |
| `memory` | async-profiler | 30s | *(deferred)* |
| `gc` | JFR | 120s | `-XX:StartFlightRecording=duration=120s,filename=/tmp/<run-id>.jfr,settings=profile` |
| `lock` | async-profiler | 30s | *(deferred)* |

For this increment, only `baseline` and `gc` are executable (JFR only).
`cpu`, `memory`, and `lock` resolve correctly but fail gracefully with a clear
message if invoked before async-profiler is available.

---

### `experiments.json` Entry (Collect Stage)

Created on first run. Subsequent runs append. One object per run:

```json
{
  "run-id": "20260416-143012",
  "timestamp": "2026-04-16T14:30:12Z",
  "target": "local-docker",
  "focus": "baseline",
  "profiler": "jfr",
  "artifact-path": "results/20260416-143012/recording.jfr",
  "analysis-path": null,
  "hypothesis": null,
  "suggested-next-focus": null,
  "files-changed": [],
  "delta": null
}
```

Fields left null in this increment are filled by later stages (Analyze, Improve,
Experiment).

---

### File and Component Changes

| File | Change |
|---|---|
| `examples/toy-app/sunwell.yml` | New — app + target config for toy-app |
| `scripts/deploy.sh` | Deleted — replaced by co-located script below |
| `.claude/skills/deploy/deploy-ssh.sh` | New (moved from `scripts/deploy.sh` via `git mv`); hardcoded config removed; accepts all config as positional args |
| `.claude/skills/profile/profile-jfr.sh` | New — SSH + run JMH with JFR flags + wait for completion |
| `.claude/skills/profile/collect-ssh.sh` | New — SCP recording from remote to `results/<run-id>/`; called by profile skill, not a standalone stage |
| `.claude/skills/deploy/SKILL.md` | Updated — reads `sunwell.yml`, resolves target, calls `deploy-ssh.sh` |
| `.claude/skills/profile/SKILL.md` | Fleshed out from stub — reads `sunwell.yml`, resolves focus, calls `profile-jfr.sh` then `collect-ssh.sh` |
| `.claude/skills/loop/SKILL.md` | Updated — orchestrates deploy → profile using `sunwell.yml` |
| `CLAUDE.md` | Updated — repo structure reflects script co-location; `scripts/` removed |

---

### Edge Cases and Failure Modes

- `sunwell.yml` not found → skill fails immediately with path it looked in and
  instructions to create it
- Named target not in `sunwell.yml` → skill fails, lists available target names
- Focus not recognized → skill fails, lists valid focus values
- async-profiler focus requested before binary is available → skill fails with
  explicit "async-profiler not yet configured" message, suggests `baseline` or `gc`
- Profile script times out or JMH exits non-zero → surface to user, do not retry
- Recording not present on remote after profile → collect fails with SSH path checked
- `results/` directory doesn't exist locally → collect creates it before SCP

---

### Deferred to Later Increments

- Analyze, Improve, Experiment, and full Loop orchestration
- async-profiler delivery and the `cpu`, `memory`, `lock` focus paths
- Non-SSH transports
- Loop resumability from `experiments.json`

---

## Increment 2 — JFR Clean Recording via `-prof jfr`

### Scope

Addresses these requirements acceptance criteria:
- Profile: "JFR profiling uses JMH's `-prof jfr` option; JMH manages recording
  lifecycle per fork (measurement-only, no warmup data), producing one clean
  recording per benchmark in a per-benchmark subdirectory"
- Collect: "All per-benchmark JFR files produced by `-prof jfr` are collected
  from the JMH output directory"

### Approach

Replace the manual `-XX:StartFlightRecording=...` JVM flag with JMH's built-in
`-prof jfr` profiler option. Pass `dir=/tmp/<run-id>` so JMH writes recordings
into the run's directory. Collect retrieves all JFR files from that directory
rather than a single hardcoded filename.

### Empirical Findings (task 11)

Tested on Docker target (JDK 21, JMH 1.37) with 2 forks. JMH uses `jcmd JFR.start`
/ `JFR.stop` per fork, scoped to the measurement phase only (warmup runs are not
recorded). All forks write to the same filename — last fork wins. Each benchmark
gets its own subdirectory.

With `dir=/tmp/<run-id>` and two benchmarks, output is:
```
/tmp/<run-id>/
  dev.sunwell.toy.CpuHogBenchmark.deduplicateTags-Throughput/profile.jfr
  dev.sunwell.toy.MemoryHogBenchmark.allocateAndDiscard-Throughput/profile.jfr
```

Last-fork-wins is acceptable: each recording contains clean measurement data from
one fork, which is the standard profiling workflow (run benchmark, inspect one
recording). Getting a separate file per fork adds statistical complexity without
profiling value.

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| JFR integration point | JMH `-prof jfr` flag | JMH scopes recording to measurement phase only; no warmup data; no manual duration needed |
| Recording output location | `-prof "jfr:dir=/tmp/<run-id>"` | Explicit dir keeps all recordings under the run-id; avoids collisions across runs |
| Per-fork file uniqueness | Last fork wins (one file per benchmark) | Confirmed empirically: all forks write to same filename; last-fork clean measurement data is sufficient |
| Collect strategy | `scp -r` the entire `/tmp/<run-id>/` dir | Captures all benchmark subdirectories; no filename prediction needed |
| `artifact-path` in experiments.json | Directory path, not single file | Multiple benchmarks → multiple files; point to `results/<run-id>/` |

### File and Component Changes

| File | Change |
|---|---|
| `.claude/skills/profile/profile-jfr.sh` | Replace `-XX:StartFlightRecording=...` with `-prof "jfr:dir=/tmp/<run-id>"`; remove `<duration>` positional arg |
| `.claude/skills/profile/collect-ssh.sh` | Accept remote dir instead of remote file; use `scp -r` to copy entire dir |
| `.claude/skills/profile/SKILL.md` | Update step 4 (profile) and step 5 (collect) to reflect new script interfaces |

### Edge Cases and Failure Modes

- If no `.jfr` files are found after profiling, collect fails with the remote path searched
- JMH output dir is benchmark-class-qualified; collect does not need to predict subdirectory names — `scp -r` copies all of them

### Deferred

- All other stages

---

## Increment 3 — Analyze

### Scope

Addresses these requirements acceptance criteria:

- All **Analyze** criteria
- Configuration: `analyze.hints` block in `sunwell.yml`

### Approach

Two-layer pipeline: scripts reduce raw JFR binary data to compact summaries;
Claude interprets compact summaries and writes `analysis.md`. No agent ever
reads raw JFR output. Claude's role is interpretation and synthesis — not
arithmetic on event counts.

Scripts are single-file Java programs run via `java Script.java`, using the
`jdk.jfr.consumer` API from the JDK standard library. Since Sunwell profiles
Java applications, a JDK is always present on the developer's machine. The API
gives typed, stable access to JFR events — no subprocess to `jfr print`, no
text parsing fragility.

Data is split along three dimensions before any agent reads it:

- **Benchmark** — one JFR file per benchmark; each is independent
- **Analysis dimension** — CPU (execution samples), allocation (object
  allocation samples), GC (collection events); each requires different events
  and different aggregation logic
- **Time window** — deferred; scripts aggregate across the full recording duration
  (see decision below)

Scripts run for each combination of benchmark × dimension, writing compact
summary files to `results/<run-id>/summaries/`. The parent skill then spawns
one subagent per benchmark, each reading all dimension summaries for its
benchmark. Subagents return structured findings. The parent reduces findings
across benchmarks into `analysis.md`.

### Empirical Findings (pre-design exploration)

Tested locally against recordings from `results/20260417-103152/`. Key findings:

- `jfr print` text format: 364 ExecutionSample events → 5,025 lines. Manageable
  for a 5-second recording; extrapolates to ~60K lines for a 60-second run.
- `jfr print --json`: same 364 events → 82,931 lines. 16× larger due to deeply
  nested class loader and module metadata on every stack frame. Not viable.
- `jdk.jfr.consumer` API: direct typed access, no subprocess, native time-range
  filtering via `RecordedEvent.getStartTime()`. Correct tool for scripts.
- `ObjectAllocationInNewTLAB` count is 0; `ObjectAllocationSample` (sampled,
  weighted) has 1,511 events with a `weight` field in bytes. The sampled event
  is the right input for allocation analysis — it represents pressure, not counts.
- GC events are compact: 53 collections in 5 seconds with full before/after heap
  snapshots. The toy app allocates ~250 MB per GC cycle with ~2–3 ms pauses.
- Thread name is present on every ExecutionSample and ObjectAllocationSample
  event — thread hints are a simple prefix filter, no JFR-level configuration needed.

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Script language | Single-file Java (`java Script.java`) | JDK guaranteed on dev machine; `jdk.jfr.consumer` is purpose-built for JFR reading; typed access, no text parsing |
| JFR reading approach | `jdk.jfr.consumer` API directly | Eliminates `jfr print` subprocess; typed fields; stable API contract; native time filtering |
| JSON output from `jfr print`? | Rejected | 16× larger than text due to nested metadata; jq paths are deep and fragile |
| Data reduction responsibility | Scripts, not Claude | Scripts aggregate/rank/compute; Claude interprets; arithmetic is not LLM work |
| Split dimensions | benchmark × analysis-dimension × time-window | No single agent reads volume that grows with recording length or benchmark count |
| Time windowing | Deferred | Scripts aggregate across full recording duration; `RecordingFile` processes event-by-event with no context window — the output is bounded by distinct hotspot count, not recording length. Temporal trend analysis is a future feature, not a current requirement. |
| Subagent granularity | One subagent per benchmark | Each reads all dimension summaries for its benchmark; parent synthesizes across benchmarks |
| Summary persistence | Written to `results/<run-id>/summaries/` | Debuggable; reusable if analysis is re-run; natural artifact alongside the recordings |
| Which dimensions per focus | See table below | Each focus has a primary signal; baseline enables all three |
| Hints application | Inside scripts, not post-processing | Scripts filter during aggregation; summaries are already hint-filtered when Claude reads them |

**Active dimensions by focus:**

| Focus | CPU | Allocation | GC |
|---|---|---|---|
| `baseline` | yes | yes | yes |
| `gc` | no | yes (allocation drives GC pressure) | yes |
| `cpu` *(future)* | yes | no | no |
| `memory` *(future)* | no | yes | yes |

### Script Interfaces

All scripts aggregate across the full recording duration.

**`summarize-cpu.java`**
```
java summarize-cpu.java <jfr-file> [--thread <pattern>] [--package <pkg>]
```
Reads `jdk.ExecutionSample` events. Groups by top-of-stack method, counts
samples, computes percentage. Output: ranked hotspot table, total sample count,
thread filter applied.

**`summarize-alloc.java`**
```
java summarize-alloc.java <jfr-file> [--thread <pattern>] [--package <pkg>]
```
Reads `jdk.ObjectAllocationSample` events. Groups by top application frame
(skips JDK internals), sums `weight` (bytes), computes percentage of total
allocation. Output: ranked allocation table by weight, total allocated bytes,
thread filter applied.

**`summarize-gc.java`**
```
java summarize-gc.java <jfr-file>
```
Reads `jdk.GarbageCollection`, `jdk.GCPhasePause`, `jdk.G1HeapSummary`,
`jdk.GCHeapSummary`, `jdk.TenuringDistribution`. Computes: collection count,
avg/max pause duration, GC frequency (collections/sec), heap usage before/after,
allocation rate (heap fill rate between collections), object promotion rate.
No thread/package hint — GC events are process-wide.

### Orchestration Flow (Analyze Skill)

1. **Read context** — read `experiments.json` to find the target `run-id` and its
   `focus`. Read `sunwell.yml` for `analyze.hints` (thread, package; both optional).

2. **Discover recordings** — glob `results/<run-id>/**/profile.jfr`. Each file is
   one benchmark. Extract benchmark name from the directory path.

3. **Run scripts** — for each benchmark × active-dimension, invoke the
   appropriate script with fully-resolved args (hints). Write output to
   `results/<run-id>/summaries/<benchmark-short-name>/<dimension>.txt`.
   Scripts for one benchmark can run in parallel.

5. **Spawn subagents** — one subagent per benchmark. Each subagent receives:
   its benchmark name, the focus, the list of summary files for its benchmark
   (all active dimensions), and the hints applied. It reads all summary files
   and returns structured findings: hotspots, allocation sites, GC behavior,
   anomalies.

6. **Reduce** — the parent reads all subagent findings (small, structured text).
   Synthesizes into `results/<run-id>/analysis.md`. Writes a cross-benchmark
   section if multiple benchmarks have related signals. Forms one hypothesis.
   Records suggested next focus.

7. **Update `experiments.json`** — set `analysis-path` to
   `results/<run-id>/analysis.md` and `suggested-next-focus` for the run entry.

### `analysis.md` Structure

```markdown
# Analysis: <run-id>

**Focus:** <focus> | **Target:** <target> | **Benchmarks:** <N>
**Hints applied:** thread=<pattern>, package=<package>  ← omit if none

## <BenchmarkName>

### CPU Hotspots      ← omitted if focus excludes CPU
### Allocation        ← omitted if focus excludes allocation
### GC Behavior       ← omitted if focus excludes GC
### Temporal Trend    ← deferred; omitted in this increment

## Cross-Benchmark Observations  ← omitted if single benchmark

## Hypothesis
<Plain-language hypothesis about the primary bottleneck.>

## Suggested Next Focus
<focus-name> — <one sentence rationale>
```

### File and Component Changes

| File | Change |
|---|---|
| `.claude/skills/analyze/SKILL.md` | New — full orchestration playbook |
| `.claude/skills/analyze/summarize-cpu.java` | New — ExecutionSample → ranked hotspot table |
| `.claude/skills/analyze/summarize-alloc.java` | New — ObjectAllocationSample → ranked allocation table by weight |
| `.claude/skills/analyze/summarize-gc.java` | New — GC events → pause stats, heap behavior, allocation rate |
| `examples/toy-app/sunwell.yml` | Add optional `analyze.hints` block (documented as example) |
| `CLAUDE.md` | Update repo structure to show analyze scripts under `analyze/` |

### Edge Cases and Failure Modes

- No JFR files found under `results/<run-id>/` → fail with path searched; suggest re-running profile
- `jdk.jfr.consumer` not available (pre-JDK 9) → script fails with JDK version and upgrade instruction; JDK 11+ required by requirements
- Thread hint matches zero events → script reports "0 events matched thread pattern X"; analysis notes the hint and continues with empty section
- Recording shorter than 30s → single time window; no temporal trend section in output
- Subagent returns no findings (empty summaries) → parent notes gap in analysis.md, does not fabricate signal
- `experiments.json` missing or has no entry for `run-id` → skill fails with instruction to run profile first

### Deferred

- async-profiler focuses (`cpu`, `memory`, `lock`) — scripts accept the same interface but these focuses aren't executable until async-profiler is delivered
- Improve, Experiment, Loop orchestration

---

## Increment 4 — Improve

### Scope

Addresses these requirements acceptance criteria:

- All **Improve** criteria

### Approach

The Improve skill is a two-phase gate: propose, then implement. Phase 1 reads
`analysis.md`, selects one targeted change, writes a proposal to
`results/<run-id>/proposal.md`, logs it in `experiments.json`, and **stops**
to wait for developer approval. Phase 2 resumes on explicit approval, applies
the change to source code, and updates `experiments.json` with the files
touched and final status.

The hard invariant: **no source file is modified before the proposal is logged
and explicitly approved.** This ensures the experiment tree records intent
before action, making every run reproducible and auditable.

A "change" is one logical concern — one method, one class, one data structure
decision. If the analysis identifies multiple problems, the skill picks the
highest-impact one and defers the rest. The diff should be minimal and
focused; a reviewer should be able to understand the full change at a glance.

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Proposal format | `proposal.md` file + unified diff embedded in it | Prose provides rationale; diff is precise and machine-readable; file is a durable artifact alongside the recording |
| Where does the proposal live? | `results/<run-id>/proposal.md` | Keeps prose/diff out of `experiments.json`; consistent with `analysis-path` pattern |
| `experiments.json` changes | Add `proposal-path` and `improvement-status` fields | Structured fields enable loop resumability; status tracks the proposal lifecycle |
| `improvement-status` values | `proposed` → `approved` → `implemented` (or `rejected`) | Minimal state machine; enough to resume a loop that was interrupted between phases |
| Does the Improve skill implement the change? | Yes, after approval | Avoids a separate "apply" step that would require re-reading all the context; the skill already holds the analysis and proposed diff |
| Approval mechanism | Developer types `approve` (with optional focus redirect) | Natural conversation gate; no ceremony; redirect allows "approve, but use gc instead" |
| How many changes per proposal? | Exactly one | Keeps the delta measurable; if multiple changes land at once, the experiment records a confounded signal; the experiment loop handles iteration |
| Choosing which change to propose | Highest-impact finding from analysis.md hypothesis | The analysis already identifies the primary bottleneck; Improve operationalizes that judgment |
| Scope limit | One logical concern — if the diff would touch more than one concern, split and propose only the highest-impact | Enforces single-variable experiments |

### `proposal.md` Structure

```markdown
# Proposal: {run-id}

**Based on:** `results/{run-id}/analysis.md`

## Change

One sentence: what is being changed and why.

## Rationale

Two to four sentences referencing specific findings from the analysis.
Cite method names, line numbers, and metrics. Explain why this change
addresses the primary bottleneck identified in the hypothesis.

## Expected Effect

What the change is expected to improve (e.g., allocation rate, CPU time),
and approximately by how much if a reasonable estimate is possible.

## Suggested Next Focus

{focus-name} — one sentence explaining why this focus is the right next step.

## Diff

```diff
--- a/{file}
+++ b/{file}
@@ ... @@
 context
-removed line
+added line
 context
```
```

### `experiments.json` Schema Changes

Two new fields added to each run entry:

```json
{
  ...
  "proposal-path": "results/<run-id>/proposal.md",
  "improvement-status": "proposed"
}
```

`improvement-status` lifecycle:
- `null` — no proposal yet (default; set by profile skill)
- `"proposed"` — `proposal.md` written; awaiting developer approval
- `"approved"` — developer approved; implementation in progress
- `"implemented"` — change applied; `files-changed` populated
- `"rejected"` — developer rejected the proposal; loop can propose an alternative

### Orchestration Flow (Improve Skill)

**Phase 1 — Propose**

1. Parse `--config <app-path>` and optional `run-id` override from arguments.
2. Read `results/experiments.json`. Identify the target run (named or most recent).
   If `improvement-status` is `"implemented"`, report "already implemented" and stop.
3. Read `results/<run-id>/analysis.md`.
4. Read source files referenced in the analysis hypothesis (the ones containing
   the identified hotspots). Extract the specific methods/lines cited.
5. Formulate one targeted change addressing the primary bottleneck. Apply the
   scope limit: one logical concern, minimum viable diff.
6. Write `results/<run-id>/proposal.md` with the structure above.
7. Update `experiments.json`: set `proposal-path` and `improvement-status: "proposed"`.
8. Present the proposal to the developer. Include:
   - The analysis hypothesis (one sentence)
   - The proposed change (from proposal.md)
   - The suggested next focus
   - The approval prompt: `"Type 'approve' to implement, or 'approve --focus <focus>' to redirect the next focus. Type 'reject' to skip."`
9. **Stop.** Do not modify any source file.

**Phase 2 — Implement (on approval)**

10. On `approve` (or `approve --focus <override>`):
    - Update `experiments.json`: set `improvement-status: "approved"`.
    - Apply the diff from `proposal.md` to the source file(s).
    - Update `experiments.json`: set `files-changed` to the list of modified files,
      `improvement-status: "implemented"`, and (if focus was redirected)
      `suggested-next-focus` to the override.
11. Report: files changed, lines modified, suggested next focus for the next run.

On `reject`:
- Update `experiments.json`: set `improvement-status: "rejected"`.
- Report: "Proposal rejected. Run `/sunwell:improve` again to generate an alternative."

### File and Component Changes

| File | Change |
|---|---|
| `.claude/skills/improve/SKILL.md` | Rewrite from stub — full two-phase orchestration playbook |
| `.claude/skills/profile/SKILL.md` | Add `proposal-path: null` and `improvement-status: null` to the `experiments.json` entry written at collect time |

### Edge Cases and Failure Modes

- `analysis.md` not found → stop with "Run `/sunwell:analyze` first"
- `experiments.json` entry missing `analysis-path` → same message
- `improvement-status: "implemented"` → report "already implemented; run profile to start a new loop iteration"
- Source file cited in analysis not found → report path and stop; do not guess an alternative
- Diff application fails (file changed since analysis) → report conflict, preserve `proposal.md`, leave status `"approved"`; developer resolves manually
- Developer provides `approve --focus <unknown>` → validate against known focus values; list valid values if unknown

### Deferred

- Experiment stage (apply change → run full loop → record delta)
- Loop orchestration that calls Improve automatically after Analyze
- Proposing multiple alternative changes in one pass

---

## Increment 5 — Experiment

### Scope

Addresses these requirements acceptance criteria:

- All **Experiment** criteria except termination conditions (deferred to Loop)

### Approach

The Experiment skill assumes the Improve skill has already applied a change to
the working tree. It deploys that change, runs profile + analyze with the
suggested focus, computes the delta vs. the baseline run, and records
everything in a new `experiments.json` entry linked to the parent.

Termination conditions ("delta meets threshold" and "three consecutive
iterations show no improvement") require multi-run state that belongs in the
Loop skill, which can see the full experiment chain. Experiment is one
iteration; Loop is the stopping condition. This keeps concerns separate.

**The delta problem:** throughput (ops/s) is the primary JMH metric, but
`profile-jfr.sh` currently pipes JMH stdout through SSH to the terminal and
discards it. Without capturing that output, throughput delta is unavailable.
The fix is one line in `profile-jfr.sh`: `tee $REMOTE_DIR/jmh-output.txt`.
The existing `collect-ssh.sh` uses `scp -r` on the entire directory, so the
file arrives home automatically. Going forward every run has
`results/<run-id>/jmh-output.txt`.

For the current baseline run (`20260417-103152`) the file won't exist —
throughput delta will be `null` for that comparison. Allocation rate from the
GC summary files is always available and makes a meaningful proxy.

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Who applies the source change? | Improve skill (already done) | Experiment assumes the working tree is in experiment state; it just deploys and measures |
| JMH stdout capture | `tee $REMOTE_DIR/jmh-output.txt` in `profile-jfr.sh` | One line; zero new moving parts; collect already copies the directory |
| Delta computation | Skill reads `jmh-output.txt` directly | JMH summary table is compact (one line per benchmark); no dedicated parsing script needed |
| Allocation rate delta | From GC summary files written by analyze | Always available; good proxy when throughput history is missing |
| experiments.json linking | `parent-run-id` field on experiment entries | Identifies the baseline being compared against; enables chain traversal in Loop |
| `delta` field format | Object with per-benchmark throughput and allocation-rate; `null` where unavailable | Handles the missing-baseline-throughput case gracefully |
| Termination conditions | Deferred to Loop (Increment 6) | Requires multi-run chain state; Experiment only knows one iteration |
| focus for experiment run | `suggested-next-focus` from the parent run's experiments.json entry | Analyze already made this judgment; Experiment operationalizes it |

### JMH Output Capture

Change to `profile-jfr.sh` — replace:
```bash
java -jar $REMOTE_PATH/$JAR_FILENAME -prof 'jfr:dir=$REMOTE_DIR'
```
with:
```bash
java -jar $REMOTE_PATH/$JAR_FILENAME -prof "jfr:dir=$REMOTE_DIR" 2>&1 | tee $REMOTE_DIR/jmh-output.txt
```

JMH writes its result table to stdout. `tee` copies it to both the terminal
(visible during the run) and `$REMOTE_DIR/jmh-output.txt`. `collect-ssh.sh`
retrieves the file alongside the JFR recordings with no changes.

### `experiments.json` Schema Changes

One new field on all run entries going forward:

```json
{
  ...
  "parent-run-id": null
}
```

For experiment runs (created by the Experiment skill), `parent-run-id` is set
to the run-id of the baseline being compared against. `delta` is populated
after analysis completes.

### `delta` Format

```json
"delta": {
  "baseline-run-id": "<parent-run-id>",
  "metrics": [
    {
      "benchmark": "CpuHogBenchmark.deduplicateTags",
      "throughput-ops-s": {
        "baseline": null,
        "experiment": 8234.5,
        "change-pct": null
      },
      "allocation-rate-mb-s": {
        "baseline": 2977.0,
        "experiment": 45.2,
        "change-pct": -98.5
      }
    }
  ]
}
```

`null` values indicate the metric was not available for that run (e.g.,
baseline predates JMH stdout capture). `change-pct` is `null` if either value
is `null`. Allocation rate comes from the GC summary files; throughput from
`jmh-output.txt`.

### JMH Output Parsing

JMH prints a result table at the end of a run:

```
Benchmark                                          Mode  Cnt     Score      Error  Units
dev.sunwell.toy.CpuHogBenchmark.deduplicateTags  thrpt   25  1234.567 ± 45.678  ops/s
dev.sunwell.toy.MemoryHogBenchmark.buildReport   thrpt   25   567.890 ± 12.345  ops/s
```

The skill reads `results/<run-id>/jmh-output.txt`, finds lines matching
`thrpt` and `ops/s`, and extracts the benchmark short name and Score value.
No dedicated script — the table is compact and the extraction is straightforward.

### Orchestration Flow (Experiment Skill)

**1. Read context**

Parse `--config <app-path>` and optional `run-id` from arguments. Read
`results/experiments.json`. Find the target run: named run-id, or most recent
entry where `improvement-status: "implemented"`.

Extract from that entry:
- `suggested-next-focus` — focus for the experiment profile run
- The baseline: the most recent entry before this one where `improvement-status`
  is `null` and `analysis-path` is set (i.e., the analyzed baseline run)

If no suitable run is found, stop with a clear message.

**2. Deploy**

Build and deploy the current working tree (which has the improvement applied):

```!
bash .claude/skills/deploy/deploy-ssh.sh \
  {host} {port} {user} {key} {jar} {remote-path}
```

If deploy fails, stop and report.

**3. Profile**

Generate a new `run-id`. Profile with `suggested-next-focus`:

```!
bash .claude/skills/profile/profile-jfr.sh \
  {host} {port} {user} {key} {remote-path} {jar-filename} {run-id}
```

**4. Collect**

```!
bash .claude/skills/profile/collect-ssh.sh \
  {host} {port} {user} {key} /tmp/{run-id} results/{run-id}
```

**5. Write experiments.json entry**

Append a new entry for this experiment run:

```json
{
  "run-id": "<new-run-id>",
  "timestamp": "<ISO-8601 UTC>",
  "target": "<target-name>",
  "focus": "<suggested-next-focus>",
  "profiler": "jfr",
  "artifact-path": "results/<new-run-id>/",
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

**6. Analyze**

Run the full analyze skill playbook inline — same steps as Increment 3.
Write `results/<new-run-id>/analysis.md` and update the new entry's
`analysis-path` in `experiments.json`.

**7. Compute delta**

Parse `results/<new-run-id>/jmh-output.txt` for throughput per benchmark.

For each benchmark, read:
- Baseline allocation rate: from `results/<baseline-run-id>/summaries/<benchmark>/gc.txt`
  (the "Allocation rate" line)
- Experiment allocation rate: from `results/<new-run-id>/summaries/<benchmark>/gc.txt`
- Baseline throughput: from `results/<baseline-run-id>/jmh-output.txt` (null if absent)
- Experiment throughput: from `results/<new-run-id>/jmh-output.txt`

Compute `change-pct` where both values are non-null.

Build the `delta` object and write it to the experiment entry in
`experiments.json`.

**8. Report**

```
Experiment Complete
─────────────────────────────────────────────
Run:      <new-run-id>
Focus:    <focus>
Parent:   <baseline-run-id>
Change:   <files-changed from parent>

Delta per benchmark:
  CpuHogBenchmark.deduplicateTags
    Throughput:      <baseline> → <experiment> ops/s  (<change-pct>%)
    Allocation rate: <baseline> → <experiment> MB/s   (<change-pct>%)

Next: run /sunwell:improve to propose the next change, or
      /sunwell:profile to start a fresh baseline.
```

### File and Component Changes

| File | Change |
|---|---|
| `.claude/skills/profile/profile-jfr.sh` | Pipe JMH stdout through `tee $REMOTE_DIR/jmh-output.txt` |
| `.claude/skills/profile/SKILL.md` | Add `parent-run-id: null` to the `experiments.json` entry template |
| `.claude/skills/experiment/SKILL.md` | Rewrite from stub — full orchestration playbook |

### Edge Cases and Failure Modes

- No `improvement-status: "implemented"` entry found → stop: "No implemented improvement found. Run `/sunwell:improve` first."
- Deploy fails → stop and report; source change remains in working tree
- Profile fails → stop; partial results not written
- `jmh-output.txt` absent on baseline run → set throughput baseline to `null`; proceed with allocation-rate delta only
- GC summary absent for a benchmark → set allocation-rate baseline or experiment to `null` for that benchmark
- Benchmark present in experiment but not baseline (or vice versa) → include in delta with `null` for the missing side; note in report

### Deferred

- Termination conditions: threshold check and three-consecutive-iterations check (Loop, Increment 6)
- Loop orchestration that chains Experiment automatically after Improve approval
- async-profiler focuses

---

## Increment 6 — Loop

### Scope

Addresses these requirements acceptance criteria:

- All **Loop** criteria

### Approach

The loop is a state machine over `experiments.json`. It orchestrates all prior
stages in sequence, delegates to each sub-skill's playbook inline (by reading
its SKILL.md and following it), and pauses at exactly one developer gate: the
Improve proposal.

The baseline phase runs once. Subsequent iterations are Improve → Experiment
pairs. After each experiment, the loop checks termination conditions. If
conditions are not met, it loops back to Improve, reading the most recent
`analysis.md` (from the experiment run) as the starting point for the next
proposal.

**The experiment run already contains a full analyze stage** (Increment 5
design). This means each experiment entry has its own `analysis.md`. The loop
never re-runs a baseline profile mid-iteration — it just uses the latest
analysis as input for the next Improve.

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Stage execution model | Loop reads sub-skill SKILL.md files and follows them inline | DRY; sub-skills stay authoritative; loop is pure orchestration, not a copy of their logic |
| Developer gate | Inherit Improve's Phase 1 STOP instruction | Improve already defines the gate; loop just follows the playbook; no separate gate mechanism |
| On reject | Stop loop; preserve state | No automatic alternative — rejection means the developer wants to take over; they can re-invoke `/sunwell:loop` or `/sunwell:improve` manually |
| Primary termination metric | `allocation-rate-mb-s` change-pct (primary); `throughput-ops-s` change-pct (secondary) | Allocation rate is always populated after Increment 5; throughput may be null for older baselines |
| Threshold: success condition | ANY benchmark improves by ≥ threshold | A single hot benchmark improving is a win; AND semantics would make it too hard to stop |
| Threshold: stall condition | ALL benchmarks fail to improve for N consecutive iterations | Stall requires consensus — if one benchmark is still moving, the loop may still be productive |
| Termination config location | `loop:` block in `sunwell.yml`; defaults if absent | Consistent with `profile.overrides` pattern; app-specific thresholds without skill changes |
| Loop iteration after experiment | Go back to Improve, reading the experiment run's analysis.md | Experiment already analyzed; no new baseline needed; analysis is always the most recent |
| Resumability | Detect state from last `experiments.json` entry | experiments.json is the ground truth; all fields needed for state detection already exist |
| Iteration reporting | `[ITERATION N] [STAGE M] <name>` | Clear progress without verbosity; iteration counter helps developer track convergence |

### Termination Thresholds in `sunwell.yml`

```yaml
# Optional — defaults apply if absent
loop:
  improvement-threshold-pct: 10   # stop when any benchmark improves this much
  stall-iterations: 3             # stop after N consecutive no-improvement iterations
```

"No improvement" for stall detection: `|change-pct| < 2%` for all benchmarks
in an experiment entry (i.e., effectively flat). This is not configurable —
sub-threshold noise should not halt the loop.

### Resumability State Machine

The loop reads `experiments.json` at startup and inspects the last entry to
determine where to enter:

| Last entry state | Resume point |
|---|---|
| No file, or empty array | `BASELINE` — start fresh |
| `analysis-path: null` | `ANALYZE` — collect succeeded; analyze interrupted |
| `analysis-path` set, `improvement-status: null` | `IMPROVE_PROPOSE` — baseline complete |
| `improvement-status: "proposed"` | `IMPROVE_PROPOSE` — re-present existing proposal |
| `improvement-status: "approved"` | `IMPROVE_IMPLEMENT` — resume Phase 2 |
| `improvement-status: "implemented"`, `delta: null` | `EXPERIMENT` — implement done; experiment not started |
| `delta` set | Check termination; if continue → `IMPROVE_PROPOSE` (new iteration) |

If the last entry has `parent-run-id` set but no `delta`, that's the EXPERIMENT
state — a previous experiment run that didn't complete. Resume from Experiment.

### Orchestration Flow

**Setup**

1. Parse `--config <app-path>`, `--target`, `--focus` from `$ARGUMENTS`.
2. Read `{app-path}/sunwell.yml`. Resolve target (arg → `default-target` →
   error) and focus (arg → `default-focus` → `baseline`).
3. Read termination config: `loop.improvement-threshold-pct` (default: 10),
   `loop.stall-iterations` (default: 3).
4. Read `results/experiments.json` (if absent → empty; state = `BASELINE`).
5. Determine initial state from last entry (see state machine above).
6. Report:
   ```
   Sunwell Loop starting — {target} / {focus}
   State: {state} | Iteration: {N}
   ```

**`BASELINE` state**

Follow the sub-skill playbooks in sequence:

- `[ITERATION 1] [STAGE 1] Deploy` — read and follow `.claude/skills/deploy/SKILL.md`
- `[ITERATION 1] [STAGE 2] Profile + Collect` — read and follow `.claude/skills/profile/SKILL.md`
- `[ITERATION 1] [STAGE 3] Analyze` — read and follow `.claude/skills/analyze/SKILL.md`

On success → advance to `IMPROVE_PROPOSE`.

**`ANALYZE` state**

- `[ITERATION N] [STAGE 3] Analyze` — follow analyze skill; update experiments.json
→ advance to `IMPROVE_PROPOSE`

**`IMPROVE_PROPOSE` state**

- `[ITERATION N] [STAGE 4] Improve — proposing`
- Follow `.claude/skills/improve/SKILL.md` Phase 1 fully (read analysis, write
  proposal, log in experiments.json, present to developer).
- **STOP.** Wait for developer response.
  - On `approve` (or `approve --focus <override>`) → advance to `IMPROVE_IMPLEMENT`
  - On `reject` → update experiments.json (`improvement-status: "rejected"`);
    report: "Loop stopped. Run `/sunwell:loop` to propose an alternative, or
    `/sunwell:improve` manually."; **stop**

**`IMPROVE_IMPLEMENT` state**

- `[ITERATION N] [STAGE 4] Improve — implementing`
- Follow `.claude/skills/improve/SKILL.md` Phase 2 (apply diff, update
  experiments.json with `files-changed` and `improvement-status: "implemented"`).
→ advance to `EXPERIMENT`

**`EXPERIMENT` state**

- `[ITERATION N] [STAGE 5] Experiment`
- Follow `.claude/skills/experiment/SKILL.md` fully (deploy → profile →
  collect → analyze → delta → update experiments.json).
→ run termination check

**Termination Check**

After each experiment completes:

1. Read the `delta` from the new entry.
2. **Success check:** for each benchmark, if either `allocation-rate-mb-s.change-pct`
   or `throughput-ops-s.change-pct` is non-null and its absolute value ≥
   `improvement-threshold-pct` → SUCCESS.
3. **Stall check:** look at the last `stall-iterations` entries that have `delta`
   set. If ALL of them have ALL benchmark metrics with `|change-pct| < 2%` (or
   null) → STALL.
4. If neither → advance to `IMPROVE_PROPOSE` with iteration counter incremented.

**Final Report**

```
Sunwell Loop Complete
─────────────────────────────────────────────
Result:     {SUCCESS | STALL | STOPPED}
Iterations: {N}
Runs:       {run-id-1}, {run-id-2}, ...

Cumulative delta (vs. {first-baseline-run-id}):
  {BenchmarkName}
    Allocation rate: {first-baseline} → {last-experiment} MB/s  ({total-pct}%)
    Throughput:      {first-baseline} → {last-experiment} ops/s ({total-pct}%)

Experiment tree: results/experiments.json
```

For SUCCESS: "Target improvement of {threshold}% reached."
For STALL: "No improvement detected in last {N} iterations."
For STOPPED (reject): omit — Improve already reported.

The cumulative delta compares the very first baseline entry to the last
experiment entry. Compute it by reading the `delta` chain from experiments.json.

### File and Component Changes

| File | Change |
|---|---|
| `.claude/skills/loop/SKILL.md` | Rewrite from stub — full orchestration playbook with state machine |
| `examples/toy-app/sunwell.yml` | Add optional `loop:` block (commented out, documented as example) |
| `spec/requirements.md` | Expand Loop acceptance criteria (done in this increment) |

### Edge Cases and Failure Modes

- experiments.json malformed → stop with parse error; do not overwrite
- Deploy fails in baseline → stop; report; no experiments.json entry written
- Deploy fails in Experiment → stop; improvement remains in working tree;
  developer resolves; on re-invoke, loop resumes at EXPERIMENT state
- Analyze fails (no JFR files) → stop; profile entry in experiments.json remains
  with `analysis-path: null`; on re-invoke, loop resumes at ANALYZE
- `improvement-status: "rejected"` on last entry → report "last proposal was
  rejected"; ask whether to regenerate or stop; default: regenerate a new proposal
  targeting the same analysis
- Loop invoked with no `experiments.json` but `--focus <non-baseline>` → warn
  "first run should use baseline focus"; continue with specified focus (developer override)
- Stall check with fewer than `stall-iterations` experiment entries → check
  available entries; report "only N of {stall-iterations} iterations available;
  stall check: {pass|fail}"

### Deferred

- async-profiler focuses (`cpu`, `memory`, `lock`)
- Cumulative delta across more than one baseline (the loop uses a single
  baseline per invocation; multi-session delta tracking is future work)
- Automatic regression revert on negative delta (deferred — developer should
  decide whether a regression is acceptable)
