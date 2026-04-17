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
