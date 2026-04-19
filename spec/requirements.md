# Requirements: async-profiler Integration and Preferred-Profiler Routing

## Problem

The profile skill currently hard-stops when `cpu`, `memory`, or `lock` focuses
are requested, because no async-profiler integration exists. This makes those
focuses unusable even though JFR could serve as a reasonable fallback.

More fundamentally, the skill treats profiler selection as a configuration
choice rather than an informed technical decision. async-profiler is
meaningfully superior to JFR for certain profiling goals, but the system has no
way to express that preference, detect availability, or fall back gracefully.

Cost of not doing this: every cpu/memory/lock profiling run fails with a
hard error, and users must remember to always pass `--focus baseline` or
`--focus gc`. The preferred-profiler model — use the best tool when available,
degrade gracefully — is not in place.

## Vision

The profile skill selects the best available profiler for each focus, based on
a documented preference order grounded in each profiler's technical strengths.
When the preferred profiler is available on the target host, it is used. When
it is not, the skill transparently falls back to the next best option and
reports which profiler was chosen and why.

async-profiler availability is probed once per profile run via SSH — the
harness never attempts to install or deploy it. If it is present (on the target
or discoverable via a well-known path), it is used; otherwise JFR covers all
focuses.

Operators may override the selected profiler per-focus in `sunwell.yml` when
they have a specific reason to diverge from the defaults.

The Docker target image ships with async-profiler pre-installed so the toy-app
loop works end-to-end without manual host preparation.

## Profiler Analysis

This section documents the technical basis for the preferred-profiler table.
It is the source of truth for all routing decisions in the skill.

### JFR (Java Flight Recorder)

**Strengths:**
- Built into JDK 11+ — zero external dependencies, always available
- Native source for GC events (`jdk.GarbageCollection`, `jdk.GCHeapSummary`,
  `jdk.YoungGarbageCollection`, etc.) — no other tool has richer GC data
- Low overhead (typically 1–3%) for most event types
- Also covers JIT compilation, thread lifecycle, I/O, and class loading

**Weakness — safepoint bias:**
JFR's `ExecutionSample` only fires at JVM safe-points. Tight loops and
non-safe-point-polling code can be under-represented or invisible. For CPU
profiling this is a significant blind spot.

### async-profiler

**Strengths:**
- Uses `AsyncGetCallTrace` + Linux `perf_events` for CPU sampling — not
  safepoint-biased. Samples wherever the thread actually is, including tight
  loops and JNI frames.
- Allocation profiling (`event=alloc`) uses TLAB callbacks and direct heap
  inspection, avoiding TLAB-boundary bias in JFR's allocation events.
- Lock/monitor contention profiling captures a wider range of contention sites.
- Can produce JFR-format output (`output=jfr`), making analysis scripts
  reusable across both profilers.

**Weakness:** external dependency; requires the native agent library
(`libasyncProfiler.so`) present on the target. Not available on all hosts.

### Preferred-Profiler Table (authoritative)

| Focus    | Preferred     | Fallback | Rationale |
|----------|---------------|----------|-----------|
| baseline | JFR           | —        | Broad overview; JFR covers all event types; no external deps |
| gc       | JFR           | —        | Native GC event source; async-profiler has no equivalent |
| cpu      | async-profiler | JFR     | Avoids safepoint bias; true CPU sampling |
| memory   | async-profiler | JFR     | Better allocation-site accuracy; TLAB-bias-free |
| lock     | async-profiler | JFR     | Wider contention visibility |

`baseline` and `gc` always use JFR regardless of async-profiler availability.

## Spikes

These are time-boxed investigations that must be completed before implementing
the affected components. Each spike produces a finding that resolves a design
question — not production code.

### Spike A — async-profiler JFR Event Format

**Question:** When async-profiler runs with `output=jfr`, does it emit the
same JFR event type names (`jdk.ExecutionSample`, `jdk.ObjectAllocationInNewTLAB`,
`jdk.ObjectAllocationOutsideTLAB`) that the existing summarize scripts query?
Or does it use its own namespace (e.g., `profiler.ExecutionSample`)?

**Method:** Boot the Docker container. Run a minimal JMH benchmark with
`-prof "async:event=cpu;output=jfr"` and `-prof "async:event=alloc;output=jfr"`.
Inspect the resulting JFR files using `jfr print` or a small Java snippet to
list all event type names present in the recording.

**Finding must answer:**
- Which event type names are present for cpu profiling?
- Which event type names are present for alloc profiling?
- Are the existing `summarize-cpu.java` and `summarize-alloc.java` scripts
  compatible as-is, or do they need to handle additional/different event types?

**Blocks:** the summarize script update decision (Goal 6) and the async-profiler
JMH integration acceptance criteria.

### Spike B — JMH async Profiler Flag Syntax

**Question:** What is the exact `-prof async:...` flag syntax accepted by the
version of JMH used in the toy-app, and does it require the async-profiler
agent path to be passed as `libPath=` or as a JVM argument? Does the JMH
`jfr` profiler and the JMH `async` profiler conflict when both are present?

**Method:** Run the toy-app benchmarks in the Docker container with candidate
flag combinations. Confirm recordings land at the expected path structure.

**Finding must answer:**
- Confirmed working flag string for cpu and alloc events, `output=jfr`
- Whether the path structure under `/tmp/<run-id>/` matches what collect-ssh.sh
  expects, or requires adjustment
- Any version-specific caveats

**Blocks:** profile skill implementation and the collect/analyze pipeline.

## Goals

1. Complete Spike A and Spike B. Record findings in design.md. Gate subsequent
   goals on those findings.

2. Document the preferred-profiler routing logic and async-profiler event
   mapping as the authoritative reference in both CLAUDE.md and the profile
   skill.

3. Probe the target host for async-profiler availability once per profile run
   via a lightweight SSH check. Do not deploy or install it.

4. For focuses that prefer async-profiler: use it when available; fall back
   to JFR when not. Report which profiler was selected and why.

5. For `baseline` and `gc`: always use JFR (no probe needed, no fallback).

6. Implement the correct JMH flags for async-profiler (per Spike B findings)
   so recordings are produced in JFR format and land in the same path structure
   the collect script expects. Map focuses to async-profiler events:
   `cpu → event=cpu`, `memory → event=alloc`.

7. Update the summarize scripts if Spike A reveals event type mismatches.
   If compatible as-is, document that fact explicitly in design.md.

8. The Docker target image includes async-profiler so the toy-app loop
   works end-to-end on `cpu` and `memory` focuses without manual host setup.

9. Allow operators to override the selected profiler per-focus in
   `sunwell.yml` so they can force a specific profiler when needed.

10. Remove the current hard-stop for `cpu`/`memory`/`lock` focuses. `lock`
    falls back to JFR pending a future lock-analysis dimension.

11. The analyze skill reads `profiler` from the experiments.json entry for
    the run being analyzed. The analysis.md header includes the profiler used.
    Subagent interpretation prompts account for safepoint bias when the
    profiler is JFR, and note its absence when the profiler is async-profiler.

## Acceptance Criteria

### Spikes

- [x] Spike A finding is documented in design.md: which JFR event type names
  async-profiler emits for cpu and alloc profiling, and whether existing
  summarize scripts are compatible as-is.

- [x] Spike B finding is documented in design.md: confirmed working JMH flag
  string and recording path structure, plus any caveats.

### Profiler Detection

- [ ] Given the target host has async-profiler installed, when any focus that
  prefers async-profiler is requested, then the skill uses async-profiler
  and reports "using async-profiler (available at <path>)".

- [ ] Given the target host does not have async-profiler, when a focus that
  prefers async-profiler is requested, then the skill falls back to JFR and
  reports "async-profiler not found on <target>; falling back to JFR".

- [ ] Given `baseline` or `gc` focus, when the profile skill runs, then it
  uses JFR without probing for async-profiler.

- [ ] The detection probe is a single lightweight SSH check (e.g.,
  `which async-profiler || ls /opt/async-profiler/lib/libasyncProfiler.so`);
  it does not attempt to install or deploy anything.

### async-profiler JMH Integration

- [ ] When async-profiler is used, the JMH profiler flag uses the confirmed
  syntax from Spike B, with the correct `event=` value per focus
  (`cpu → event=cpu`, `memory → event=alloc`) and `output=jfr`.

- [ ] The per-benchmark recording files land at the path structure confirmed
  by Spike B — consistent with what collect-ssh.sh expects.

- [ ] Given a completed async-profiler cpu run, when the analyze skill
  processes the recordings, then it produces a populated CPU Hotspots section
  (not empty, not an error).

- [ ] Given a completed async-profiler memory run, when the analyze skill
  processes the recordings, then it produces a populated Allocation section
  (not empty, not an error).

- [ ] If Spike A reveals event type mismatches, the summarize scripts are
  updated to handle async-profiler's event types in addition to standard JFR
  event types. If compatible as-is, this criterion is satisfied by
  the Spike A finding alone.

### Profiler Context in Analysis

- [ ] The `profiler` field in experiments.json is populated for every run
  (already in the schema; this confirms it is always set, not null).

- [ ] The analyze skill reads `profiler` from the run's experiments.json entry
  and includes it in the analysis.md header
  (`**Profiler:** jfr` or `**Profiler:** async-profiler`).

- [ ] Given a JFR-profiled run, when the analyze skill generates its subagent
  interpretation prompt, then the prompt notes that ExecutionSample results
  may be affected by safepoint bias.

- [ ] Given an async-profiler run, when the analyze skill generates its
  subagent interpretation prompt, then the prompt notes that safepoint bias
  is not a concern for this data.

### Override

- [ ] `sunwell.yml` accepts an optional `profile.profiler-override` block
  that maps focus names to profiler names (e.g., `cpu: jfr`).

- [ ] Given a `profiler-override` entry for a focus, when that focus is
  profiled, then the overridden profiler is used and a note is logged:
  "profiler override: using <profiler> for focus <focus>".

- [ ] `sunwell.yml` for the toy-app includes a commented-out example of the
  override block.

### Docker Target

- [ ] The Docker image installs async-profiler during image build (not at
  run time) at a well-known path (e.g., `/opt/async-profiler/`).

- [ ] Given the toy-app container is running, when `--focus cpu` is profiled,
  then a JFR recording is produced, collected, and analyzed successfully.

- [ ] Given the toy-app container is running, when `--focus memory` is
  profiled, then a JFR recording is produced, collected, and analyzed
  successfully.

- [ ] Given the toy-app container is running, when `--focus lock` is
  profiled, then a JFR recording is produced and collected successfully.
  (Analysis for lock is deferred — see Out of Scope.)

### Documentation

- [ ] CLAUDE.md profiler table is updated to reflect the preferred/fallback
  model with rationale (replacing the current simple table).

- [ ] The profile skill description and focus routing table are updated to
  match the new detection-and-fallback logic.

## Out of Scope

- **Lock analysis dimension** — `lock` focus profiling (collection) is in scope,
  but a `summarize-lock.java` script and the analyze skill's dimension table
  update for lock are deferred until async-profiler is confirmed working for
  `cpu` and `memory`. Lock analysis is the next story.
- Deploying or installing async-profiler onto target hosts (sense-only)
- async-profiler output formats other than JFR (flame graphs, collapsed stacks)
- Non-Linux targets (async-profiler requires perf_events; Windows/macOS are out)
- Plugin marketplace publication
- Extracting `examples/` to a separate repo
- Extracting the spec workflow skill to a personal skill
