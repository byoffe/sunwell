# Sunwell — Working Notes

> This file is temporary. It absorbs session-to-session churn.
> Permanent decisions get promoted to CLAUDE.md.
> This file gets deleted when the project matures.

## Status

Session 1 complete. Staged and ready to commit/push to GitHub.

## Session 2 Goals

1. deploy script (`scripts/deploy.sh`) — thin wrapper:
   - `mvn package -pl examples/toy-app --also-make`
   - `scp` uber JAR to target server
   - SSH verify (java -version, confirm JAR landed)
2. `perf-deploy` Claude command (`.claude/commands/perf-deploy.md`) — playbook
   that calls the deploy script and reports success/failure

## Upcoming Sessions (rough order, subject to change)

- Session 3: profile script + perf-profile Claude command
  (SSH in → launch with JFR flags → collect .jfr)
- Session 4: perf-analyze Claude command
  (jfr print --json → Claude reads → writes analysis.md)
- Session 5: async-profiler support
- Session 6: HTTP load adapter (unlocks Spring Boot use case)
- Session 7: Maven plugin (distribution mechanism)

## Open Questions / Parking Lot

- What does a perf target YAML actually look like? (design when needed)
- How does Claude command reference the correct target YAML?
- async-profiler binary delivery mechanism (scp for now, Ansible later)
- When do examples/ move to their own repo?

## Session Log

### Session 1 — Complete
- [x] Parent POM (`pom.xml`, modules: `harness`, `examples/toy-app`)
- [x] harness module stub (`harness/pom.xml`)
- [x] toy-app module:
  - [x] `CpuHog.java` — regex recompile via `String.replaceAll()` + O(n²) dedup
  - [x] `MemoryHog.java` — string concat loop + defensive copies + unboxing
  - [x] `CpuHogBenchmark.java` + `MemoryHogBenchmark.java` (JMH 1.37)
  - [x] `pom.xml` — JMH deps + Maven Shade uber JAR (`toy-app-benchmarks.jar`)
- [x] Docker setup:
  - [x] `eclipse-temurin:21-jdk` + sshd, user `sunwell`
  - [x] Key-based SSH auth (dev keypair gitignored, generated locally)
  - [x] Java on PATH for non-interactive SSH sessions (`/etc/environment`)
  - [x] `localhost:2222 → container:22` confirmed working
- [x] `.gitignore`, `.claude/settings.json` (empty — no hooks)
- [x] 14 files staged, ready to commit

### Session 2
- [ ] deploy script
- [ ] perf-deploy Claude command
