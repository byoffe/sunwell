# Feedback

Free-form capture of questions, concerns, and observations on work in progress.
Not a spec artifact — no required structure. Gets processed into requirements,
design changes, or task revisions, then deleted at compress-and-close.

---

## Staged Session 2 Files (pending review)

> Files waiting for review before commit:
> `.claude-plugin/plugin.json`, `scripts/deploy.sh`, `skills/deploy/SKILL.md`,
> `skills/loop/SKILL.md`, `skills/profile/SKILL.md`, `skills/analyze/SKILL.md`,
> `skills/improve/SKILL.md`, `skills/experiment/SKILL.md`, `CLAUDE.md`, `WORKING.md`

### scripts/deploy.sh

Current script mixes three concerns that should be separated:

1. **Transport logic** — SSH/SCP mechanics. This is correct as one script per
   transport type (not per environment). `local-docker` and remote bare metal
   both use SSH — same transport, different config.

2. **Target configuration** — host, port, SSH key hardcoded in a `case`
   statement. Should be named targets in a `sunwell.yml` config file that
   lives with the app.

3. **App configuration** — JAR path and Maven module hardcoded. Should also
   live in `sunwell.yml` with the app, not in the harness script.

**Resolution:** Spec out the perf-target YAML schema before refactoring.
The script stays as a working prototype until that spec is approved.
See design discussion captured in plan file (2026-04-16).

### .claude/skills/deploy/SKILL.md

Updated to document the three deployment concerns and the intended end-state
(transport scripts + perf-target YAML + app config). YAML schema explicitly
marked as unfinalized, pending perf-target spec.

---

## JFR Recording Overwritten Per JMH Fork (2026-04-17)

Discovered during first live profile run. `profile-jfr.sh` passes
`-XX:StartFlightRecording=filename=/tmp/<run-id>.jfr` to the JMH coordinator
JVM. JMH forks a new JVM per benchmark, inheriting those flags. Each fork
writes to the same filename — the second benchmark's recording overwrites
the first. Only the last benchmark's JFR data survives.

**Root cause:** JFR flags are passed at the coordinator level, but profiling
happens in forked JVMs. Multiple forks, one filename.

**Preferred fix:** Use JMH's built-in `-prof jfr` profiler option. JMH manages
per-fork recording filenames automatically and understands fork lifecycle.
This is the designed integration point.

**Impact on current results:** The `results/20260417-022041/recording.jfr`
collected in the first test run contains only `MemoryHogBenchmark` data.
`CpuHogBenchmark` JFR data was overwritten.

**Action:** Update `profile-jfr.sh` and `profile/SKILL.md` to use
`-prof jfr` instead of manual `StartFlightRecording` flags. Research
where JMH deposits per-fork JFR files when using `-prof jfr` and update
`collect-ssh.sh` to retrieve them.
