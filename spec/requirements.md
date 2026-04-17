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

9. **Experiment tree.** `results/experiments.json` is the persistent record of
   every run. Each entry records: run-id, timestamp, target, focus, profiler,
   artifact path, analysis path, hypothesis, suggested next focus, files changed,
   and measured delta. Claude reads this file at the start of each session to
   restore continuity.

10. **Autonomous loop.** `/sunwell:loop` runs all stages in sequence without
    manual intervention, except at explicit developer gates (approve proposed
    change and focus for next iteration). The loop is resumable — it can pick
    up mid-run from `experiments.json`.

## Acceptance Criteria

### Configuration
- [ ] `sunwell.yml` schema is defined and documented; canonical shape:
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
  ```
- [ ] `examples/toy-app/sunwell.yml` exists and is valid per the schema
- [ ] All skills resolve app and target config from `sunwell.yml`; nothing hardcoded
- [ ] Skills own the focus→profiler/flags/duration defaults table; `sunwell.yml`
      `profile.overrides` block applies on top when present

### Deploy
- [ ] `/sunwell:deploy [target]` reads config from `sunwell.yml`, defaults to
      `default-target` when no target is named
- [ ] Deploy verifies JAR landed and Java is available on the remote host
- [ ] Deploy script is transport-only — no app or target config embedded in it

### Profile
- [ ] `/sunwell:profile [target] [--focus <focus>]` SSHs in, translates focus
      to profiler/flags/duration, runs benchmark JAR, waits for completion
- [ ] Focus defaults to `default-focus` in `sunwell.yml` when not specified
- [ ] Recording saved to `results/<run-id>/recording.<ext>` (`.jfr` for JFR)
- [ ] `run-id` is deterministic and human-readable (e.g., `20260416-143012`)
- [ ] Focus and resolved profiler are recorded in `experiments.json`

### Collect
- [ ] Recording is copied back from remote host to local `results/<run-id>/`
- [ ] `results/experiments.json` is created on first run; subsequent runs append

### Analyze
- [ ] `/sunwell:analyze` reads the recording and writes `results/<run-id>/analysis.md`
      tailored to the focus used for that run
- [ ] Analysis identifies hotspots relevant to focus (CPU, allocation, GC, locks)
- [ ] Analysis is written in plain language, not raw profiler output
- [ ] Claude forms a hypothesis and records a suggested next focus

### Improve
- [ ] `/sunwell:improve` proposes one targeted change based on `analysis.md`
- [ ] Proposal includes: code change, rationale, and suggested focus for next run
- [ ] Proposal is logged to `experiments.json` before any code is modified
- [ ] Developer must explicitly approve change and confirm (or redirect) next focus

### Experiment
- [ ] `/sunwell:experiment` applies the approved change and runs the full loop
      with the confirmed focus
- [ ] Delta (throughput, latency, allocation rate) is recorded vs. baseline
- [ ] Loop terminates when delta meets threshold or three consecutive iterations
      show no improvement

### Loop
- [ ] `/sunwell:loop` runs deploy → profile → collect → analyze → improve →
      experiment in sequence
- [ ] Developer gates: approve proposed change + confirm focus for next run
- [ ] Loop is resumable from `experiments.json` if interrupted

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
