# Requirements: Sunwell — Full Loop

## Problem

Tuning Java application performance is slow and manual. The typical cycle is:
profile locally, squint at a flame graph, make a guess, re-run, repeat — with
no systematic record of what was tried, what the baseline was, or whether a
change actually helped. Claude can participate in every stage of that cycle,
but only if the stages are structured, reproducible, and connected.

## Vision

Sunwell closes the loop between code changes and empirical performance data.
Claude runs the loop — build, deploy, profile, analyze, propose a change,
measure the delta, repeat — with the developer reviewing decisions at each
gate. The loop is invariant. What varies is configuration.

    Build → Deploy → Profile → Collect → Analyze → Improve → Experiment → Repeat

## Goals

1. **Configuration-driven, not code-driven.** A `sunwell.yml` file lives with
   each profiled app. It describes the app (JAR, Maven module), named deployment
   targets (host, port, credentials, remote path), and optional profiling
   overrides. No app or target config is hardcoded in any skill or script.

2. **Focus-driven profiling.** The developer specifies a *focus* — what they
   want to understand: `cpu`, `memory`, `gc`, `lock`, or `baseline`. The profile
   skill owns a defaults table that translates focus into profiler choice,
   JVM flags, and duration. The developer never configures profiler or flags
   directly unless overriding a default.

   | Focus | Profiler | Duration | Notes |
   |---|---|---|---|
   | `baseline` | JFR | 60s | Full recording; starting point for every new app |
   | `cpu` | async-profiler | 30s | event=cpu; CPU hotspot sampling |
   | `memory` | async-profiler | 30s | event=alloc; allocation pressure |
   | `gc` | JFR | 120s | GC behavior needs longer observation window |
   | `lock` | async-profiler | 30s | event=lock; thread contention |

   Focus is a *hypothesis*, not a static setting. It typically changes between
   loop iterations as understanding of the bottleneck evolves. CPU and memory
   are often trade-offs — fixing one can expose the other.

3. **Deploy stage.** Build the JAR, copy it to the target via the appropriate
   transport, verify it landed. Currently SSH/SCP; other transports (k8s, ECS)
   are future.

4. **Profile stage.** SSH into the target, run the JMH benchmark with the flags
   derived from the current focus, collect the output file, save it to
   `results/<run-id>/`. Focus is passed at invocation or read from
   `default-focus` in `sunwell.yml`.

5. **Collect stage.** Copy the profiling artifact back from the remote host to
   `results/<run-id>/`. Normalize the filename. Update the experiment tree.

6. **Analyze stage.** Parse the profiling artifact into a structured
   `results/<run-id>/analysis.md` tailored to the focus used. Claude reads the
   analysis and forms a hypothesis about the next bottleneck to investigate.

7. **Improve stage.** Claude proposes one concrete, targeted code change based
   on the analysis. The proposal — including the suggested focus for the next
   run — is logged in the experiment tree before any code is touched. Developer
   approves before implementation.

8. **Experiment stage.** Apply the approved change, run the full loop again with
   the suggested focus, record the delta vs. baseline. Loop continues until the
   delta meets an acceptance threshold or three consecutive iterations show no
   improvement.

9. **Experiment tree.** `{app-path}/sunwell-results/experiments.json` is the
   persistent record of every run, co-located with `sunwell.yml`. Each entry
   records: run-id, timestamp, target, focus, profiler, artifact path, analysis
   path, hypothesis, suggested next focus, files changed, and measured delta.
   Claude reads this file at the start of each session to restore continuity.

10. **Autonomous loop.** `/sunwell:loop` runs all stages in sequence without
    manual intervention, except at explicit developer gates (approve proposed
    change and focus for next iteration). The loop is resumable — it can pick
    up mid-run from `experiments.json`.

## Acceptance Criteria

### Configuration
- [x] `sunwell.yml` schema is defined and documented; canonical shape:
  ```yaml
  app: toy-app
  maven:
    module: examples/toy-app
  jar: examples/toy-app/target/toy-app-benchmarks.jar
  default-target: local-docker
  default-focus: baseline

  targets:
    local-docker:
      transport: ssh
      host: localhost
      port: 2222
      user: sunwell
      key: examples/docker/sunwell_dev_key
      remote-path: /home/sunwell

  # Optional — only needed to override skill defaults:
  profile:
    overrides:
      gc:
        duration: 240s

  # Optional — narrow analysis to the code you care about:
  analyze:
    hints:
      thread: pricing-worker     # match thread name by prefix/pattern
      package: com.example.pricing  # restrict stack frames to this package
  ```
- [x] `examples/toy-app/sunwell.yml` exists and is valid per the schema
- [x] All skills resolve app and target config from `sunwell.yml`; nothing hardcoded
- [x] Skills own the focus→profiler/flags/duration defaults table; `sunwell.yml`
      `profile.overrides` block applies on top when present
- [x] `sunwell.yml` supports an optional `analyze.hints` block: `thread` (name
      prefix/pattern) and `package` (restrict stack frames); both optional and
      independent

### Deploy
- [x] `/sunwell:deploy [target]` reads config from `sunwell.yml`, defaults to
      `default-target` when no target is named
- [x] Given a deploy completes, when verification runs, then the JAR exists at remote-path and `java -version` succeeds on the target host
- [x] Deploy script is transport-only — no app or target config embedded in it

### Profile
- [x] `/sunwell:profile [target] [--focus <focus>]` SSHs in, translates focus
      to profiler/flags/duration, runs benchmark JAR, waits for completion
- [x] Given sunwell.yml has `default-focus` set, when `/sunwell:profile` is invoked without `--focus`, then the focus from `default-focus` is used
- [x] JFR profiling uses JMH's `-prof jfr` option; JMH manages recording
      lifecycle per fork (measurement-only, no warmup data), producing one clean
      recording per benchmark in a per-benchmark subdirectory
- [x] `run-id` is deterministic and human-readable (e.g., `20260416-143012`)
- [x] Focus and resolved profiler are recorded in `experiments.json`

### Collect
- [x] Recording is copied back from remote host to local
      `{app-path}/sunwell-results/<run-id>/` (co-located with `sunwell.yml`,
      not at the repo root)
- [x] All per-benchmark JFR files produced by `-prof jfr` are collected from
      the JMH output directory
- [x] `{app-path}/sunwell-results/experiments.json` is created on first run;
      subsequent runs append
- [x] `{app-path}/sunwell-results/` is gitignored at the app level

### Analyze
- [x] `/sunwell:analyze` reads the recording and writes
      `{app-path}/sunwell-results/<run-id>/analysis.md` tailored to the focus
      used for that run
- [x] Analysis identifies hotspots relevant to focus (CPU, allocation, GC, locks)
- [x] Analysis is written in plain language, not raw profiler output
- [x] Claude forms a hypothesis and records a suggested next focus
- [x] If `analyze.hints` is present in `sunwell.yml`, analysis is filtered to
      the specified thread pattern and/or package; the report states which hints
      were applied
- [x] Hints apply to all focuses — they narrow interpretation, not recording scope

### Improve
- [x] `/sunwell:improve` proposes one targeted change based on `analysis.md`
- [x] Proposal includes: code change, rationale, and suggested focus for next run
- [x] Given `/sunwell:improve` formulates a proposal, when proposal.md is written and experiments.json updated with `improvement-status: "proposed"`, then no source file has been modified yet
- [x] Given a proposal is presented, when the developer does not type `approve`, then no source files are modified

### Experiment
- [x] `/sunwell:experiment` applies the approved change and runs the full loop
      with the confirmed focus
- [x] Delta (throughput, latency, allocation rate) is recorded vs. baseline
- [x] Loop terminates when delta meets threshold or three consecutive iterations
      show no improvement

### Loop
- [x] `/sunwell:loop` runs: baseline (deploy → profile → collect → analyze),
      then iterates (improve → [gate] → experiment) until termination
- [x] Given the loop reaches an Improve proposal, when the developer has not typed `approve`, then the loop is paused and no further stages execute; on `reject`, loop stops and preserves state in experiments.json
- [x] Termination condition A (SUCCESS): ALL benchmarks meet
      `loop.improvement-threshold-pct` (default 10%) in allocation rate or
      throughput — loop reports SUCCESS and stops. A single benchmark improving
      does not halt the session; all must converge.
- [x] Termination condition B (STALL): the last `loop.stall-iterations`
      (default 3) consecutive experiment entries all show no improvement across
      all benchmarks — loop reports STALL and stops
- [x] Termination thresholds configurable in `sunwell.yml` under a `loop:`
      block; defaults apply if the block is absent
- [x] Given experiments.json records a partial run state, when `/sunwell:loop` is invoked, then it resumes from the interrupted stage without re-running completed stages
- [x] Progress reported per stage: `[ITERATION N] [STAGE M] <name> — <status>`
- [x] The project-level skill name does not conflict with the built-in loop
      scheduler; the sunwell loop is invokable unambiguously in a fresh session

### Clean
- [x] `/sunwell:clean [--config <app-path>]` resets the app to a clean state:
      reads `experiments.json` for all `files-changed` entries and reverts each
      file via `git restore`, then deletes `{app-path}/sunwell-results/`
- [x] Given `/sunwell:clean` is invoked, when the confirmation summary is presented, then no files have been reverted and no directories deleted yet
- [x] Given the confirmation summary is presented, when the developer does not type `confirm`, then no files are reverted and no directories are deleted
- [x] Reports what was reverted and deleted; leaves working tree clean

## JDK Compatibility

Current target is JDK 11+. JFR was open-sourced in JDK 11 and requires no
special flags from JDK 11 onward. The `-XX:+FlightRecorder` flag is unnecessary
on JDK 11–12 and deprecated on JDK 13+; `profile-jfr.sh` omits it.

Future requirement: `profile-jfr.sh` should detect the JDK version on the
remote host and tailor JVM flags accordingly:

| JDK | JFR flags |
|---|---|
| 8 (Oracle) | `-XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:StartFlightRecording=...` |
| 11–12 | `-XX:StartFlightRecording=...` |
| 13+ | `-XX:StartFlightRecording=...` (identical; no deprecated flags) |

JDK 8 support requires Oracle JDK (commercial features) and is not a current
priority. Detection should be non-breaking — fail clearly if the sensed version
is unsupported rather than passing flags that may be rejected.

## Out of Scope

- async-profiler delivery and integration (JFR only until the loop is working
  end-to-end; the focus table is designed for async-profiler but won't invoke it yet)
- Multiple concurrent targets or parallel profiling
- Non-SSH transports (Kubernetes, ECS) — transport script accommodates them;
  implementing them is deferred
- CI/CD integration
- Plugin marketplace publication
- Extracting `examples/` to a separate repo (deferred until harness is stable)
- Extracting the spec workflow skill to a personal skill (deferred until a
  second project needs it)
