# Tasks: Sunwell — Full Loop

## Increment 1 — Configuration + Deploy + Profile + Collect

- [x] 1. Create `examples/toy-app/sunwell.yml` with app config, `local-docker`
         target, and `default-focus: baseline`
- [x] 2. `git mv scripts/deploy.sh .claude/skills/deploy/deploy-ssh.sh`
- [x] 3. Rewrite `deploy-ssh.sh` to accept all config as positional args
         (`<host> <port> <user> <key> <local-jar> <remote-path>`); remove all
         hardcoded values and the target `case` statement
- [x] 4. Update `.claude/skills/deploy/SKILL.md` to read `sunwell.yml`, resolve
         the named target, and call `deploy-ssh.sh` with fully-resolved args
- [x] 5. Create `.claude/skills/profile/profile-jfr.sh` — SSHes in, runs JAR
         with JFR flags, waits for completion; leaves recording at
         `/tmp/<run-id>.jfr` on the remote host; accepts
         `<host> <port> <user> <key> <remote-path> <jar-filename> <duration> <run-id>`
- [x] 6. Create `.claude/skills/profile/collect-ssh.sh` — SCPs
         `/tmp/<run-id>.jfr` from remote to `results/<run-id>/recording.jfr`
         locally; creates `results/<run-id>/` if absent; accepts
         `<host> <port> <user> <key> <remote-file> <local-dir>`
- [x] 7. Flesh out `.claude/skills/profile/SKILL.md`: read `sunwell.yml`,
         resolve focus (CLI arg → `default-focus` → `baseline`), apply any
         `profile.overrides`, call `profile-jfr.sh` then `collect-ssh.sh`,
         write the `experiments.json` entry with nulls for analyze/improve fields
- [x] 8. Update `.claude/skills/loop/SKILL.md` to run deploy → profile in
         sequence, reading target and focus from `sunwell.yml`
- [x] 9. Update `CLAUDE.md` repo structure: remove `scripts/` entry, add
         co-located scripts under each skill directory
- [x] 10. Run `git status` — confirm no untracked files; `git add` or
          `.gitignore` anything that floats

## Increment 2 — JFR Per-Fork Recording Fix

- [x] 1. SSH into the Docker target and run a minimal JMH benchmark with
         `-prof jfr` to confirm where JMH writes per-fork JFR files; document
         the output path in design.md before touching any scripts
- [x] 2. Update `profile-jfr.sh` to pass `-prof jfr` to JMH instead of
         `-XX:StartFlightRecording=...`; remove the `<duration>` positional arg
- [x] 3. Update `collect-ssh.sh` to SCP all `*.jfr` files from the JMH output
         directory rather than a single `/tmp/<run-id>.jfr`
- [x] 4. Update `profile/SKILL.md` steps 4 and 5 to reflect the new script
         interfaces
- [x] 5. Run a live profile + collect to confirm all per-fork recordings land
         in `results/<run-id>/`
- [x] 6. Run `git status` — confirm no untracked files

## Increment 3 — Analyze

- [ ] 1. Create `.claude/skills/analyze/summarize-cpu.java` — reads
         `jdk.ExecutionSample` events via `jdk.jfr.consumer.RecordingFile`;
         groups by top-of-stack method; ranks by sample count with percentage;
         accepts `<jfr-file> [--thread <pattern>] [--package <pkg>]`; prints
         ranked hotspot table + total sample count + filter applied
- [ ] 2. Create `.claude/skills/analyze/summarize-alloc.java` — reads
         `jdk.ObjectAllocationSample` events; groups by top application frame
         (skips JDK internal frames until a non-`java`/`jdk`/`sun` frame is
         found); sums `weight` in bytes; ranks by total weight with percentage;
         accepts `<jfr-file> [--thread <pattern>] [--package <pkg>]`; prints
         ranked allocation table + total bytes + filter applied
- [ ] 3. Create `.claude/skills/analyze/summarize-gc.java` — reads
         `jdk.GarbageCollection`, `jdk.GCPhasePause`, `jdk.G1HeapSummary`,
         `jdk.GCHeapSummary`, `jdk.TenuringDistribution`; computes: collection
         count, avg/max pause duration, GC frequency (collections/sec), avg
         heap before/after GC, allocation rate (MB/s), promotion rate; prints
         compact GC summary; no hint args (GC is process-wide)
- [ ] 4. Smoke-test all three scripts locally against existing recordings in
         `results/20260417-103152/`; confirm output is compact and correct;
         fix any parsing issues before writing the skill
- [ ] 5. Create `.claude/skills/analyze/SKILL.md` — orchestration playbook:
         read `experiments.json` for run-id + focus; read `sunwell.yml` for
         `analyze.hints`; glob `results/<run-id>/**/profile.jfr` to discover
         benchmarks; determine active dimensions from focus table; run scripts
         per benchmark × active-dimension writing summaries to
         `results/<run-id>/summaries/<benchmark-short-name>/<dimension>.txt`;
         spawn one subagent per benchmark to interpret its summaries; reduce
         subagent findings into `results/<run-id>/analysis.md`; update
         `experiments.json` with `analysis-path` and `suggested-next-focus`
- [ ] 6. Add optional `analyze.hints` block (commented out) to
         `examples/toy-app/sunwell.yml` showing `thread` and `package` fields
- [ ] 7. Update `CLAUDE.md` repo structure to list the three analyze scripts
         under `analyze/`
- [ ] 8. Run `/sunwell:analyze` end-to-end against an existing run-id; verify
         `results/<run-id>/summaries/` populated, `analysis.md` written in
         plain language, `experiments.json` updated with `analysis-path` and
         `suggested-next-focus`
- [ ] 9. `git status` — confirm no untracked files; `git add` or `.gitignore`
         anything that floats

## Notes

- Tasks 1–4 are deploy-side; tasks 5–8 are profile-side. Each group is
  independently testable: deploy can be verified with `/sunwell:deploy` before
  touching profile.
- Task 3 (`deploy-ssh.sh`) replaces the existing script in place after the
  `git mv` — rewrite it entirely rather than patching the old case statement.
- The `scripts/` directory will be empty after task 2 and will disappear from
  git tracking automatically; no explicit deletion needed.
- `experiments.json` is created by task 7 (profile skill) on first run.
  The file lives in `results/` which is already gitignored.
- For task 7, `cpu`, `memory`, and `lock` focuses should resolve but exit with
  a clear "async-profiler not yet configured" message if invoked — do not
  silently fall back to JFR.
