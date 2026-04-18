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

- [x] 1. Create `.claude/skills/analyze/summarize-cpu.java` — reads
         `jdk.ExecutionSample` events via `jdk.jfr.consumer.RecordingFile`;
         groups by top-of-stack method; ranks by sample count with percentage;
         accepts `<jfr-file> [--thread <pattern>] [--package <pkg>]`; prints
         ranked hotspot table + total sample count + filter applied
- [x] 2. Create `.claude/skills/analyze/summarize-alloc.java` — reads
         `jdk.ObjectAllocationSample` events; groups by top application frame
         (skips JDK internal frames until a non-`java`/`jdk`/`sun` frame is
         found); sums `weight` in bytes; ranks by total weight with percentage;
         accepts `<jfr-file> [--thread <pattern>] [--package <pkg>]`; prints
         ranked allocation table + total bytes + filter applied
- [x] 3. Create `.claude/skills/analyze/summarize-gc.java` — reads
         `jdk.GarbageCollection`, `jdk.GCPhasePause`, `jdk.G1HeapSummary`,
         `jdk.GCHeapSummary`, `jdk.TenuringDistribution`; computes: collection
         count, avg/max pause duration, GC frequency (collections/sec), avg
         heap before/after GC, allocation rate (MB/s), promotion rate; prints
         compact GC summary; no hint args (GC is process-wide)
- [x] 4. Smoke-test all three scripts locally against existing recordings in
         `results/20260417-103152/`; confirm output is compact and correct;
         fix any parsing issues before writing the skill
- [x] 5. Create `.claude/skills/analyze/SKILL.md` — orchestration playbook:
         read `experiments.json` for run-id + focus; read `sunwell.yml` for
         `analyze.hints`; glob `results/<run-id>/**/profile.jfr` to discover
         benchmarks; determine active dimensions from focus table; run scripts
         per benchmark × active-dimension writing summaries to
         `results/<run-id>/summaries/<benchmark-short-name>/<dimension>.txt`;
         spawn one subagent per benchmark to interpret its summaries; reduce
         subagent findings into `results/<run-id>/analysis.md`; update
         `experiments.json` with `analysis-path` and `suggested-next-focus`
- [x] 6. Add optional `analyze.hints` block (commented out) to
         `examples/toy-app/sunwell.yml` showing `thread` and `package` fields
- [x] 7. Update `CLAUDE.md` repo structure to list the three analyze scripts
         under `analyze/`
- [x] 8. Run `/sunwell:analyze` end-to-end against an existing run-id; verify
         `results/<run-id>/summaries/` populated, `analysis.md` written in
         plain language, `experiments.json` updated with `analysis-path` and
         `suggested-next-focus`
- [x] 9. `git status` — confirm no untracked files; `git add` or `.gitignore`
         anything that floats

## Increment 4 — Improve

- [x] 1. Update `.claude/skills/profile/SKILL.md` step 6 (experiments.json entry):
         add `"proposal-path": null` and `"improvement-status": null` to the JSON
         template written at collect time
- [x] 2. Rewrite `.claude/skills/improve/SKILL.md` — full two-phase playbook:
         - Phase 1: parse args; read experiments.json for target run; check
           `improvement-status` (stop if already `"implemented"`); read
           `analysis.md`; read source files cited in the hypothesis; formulate
           one targeted change (one logical concern, minimum viable diff); write
           `results/<run-id>/proposal.md` with change + rationale + expected
           effect + suggested focus + unified diff; update experiments.json with
           `proposal-path` and `improvement-status: "proposed"`; present proposal
           to developer with approval prompt; **stop**
         - Phase 2 (on `approve`): update status to `"approved"`; apply diff;
           update `files-changed`, `improvement-status: "implemented"`, and
           optionally `suggested-next-focus` if `--focus` override given; report
         - Phase 2 (on `reject`): update `improvement-status: "rejected"`; report
- [x] 3. Run `/sunwell:improve --config examples/toy-app` end-to-end against the
         `20260417-103152` run: verify `proposal.md` is written before any source
         is modified, experiments.json shows `"improvement-status": "proposed"`,
         then approve and verify the change is applied and `files-changed` is
         populated
- [x] 4. `git status` — confirm no untracked files

## Increment 5 — Experiment

- [x] 1. Update `profile-jfr.sh`: pipe JMH stdout through
         `tee $REMOTE_DIR/jmh-output.txt` so throughput results are captured
         alongside JFR recordings and collected home automatically
- [x] 2. Update `.claude/skills/profile/SKILL.md` step 6 (experiments.json
         entry): add `"parent-run-id": null` to the JSON template
- [x] 3. Rewrite `.claude/skills/experiment/SKILL.md` — full orchestration
         playbook:
         - Read experiments.json; find most recent `improvement-status:
           "implemented"` entry; identify baseline (most recent prior entry
           with `analysis-path` set)
         - Deploy current working tree (improvement already applied)
         - Generate new run-id; profile with `suggested-next-focus`; collect
         - Write new experiments.json entry with `parent-run-id` set
         - Run full analyze inline; update `analysis-path` in new entry
         - Parse `jmh-output.txt` for throughput; read GC summaries for
           allocation rate; compute delta vs. baseline for each benchmark
         - Write `delta` object to new entry in experiments.json
         - Report delta table with throughput and allocation-rate changes
- [x] 4. Run `/sunwell:experiment --config examples/toy-app` end-to-end:
         verify `jmh-output.txt` lands in `results/<new-run-id>/`, a new
         experiments.json entry is written with `parent-run-id` set,
         `delta` is populated, and the report shows allocation-rate change
         for `CpuHogBenchmark.deduplicateTags`
- [x] 5. `git status` — confirm no untracked files

## Increment 6 — Loop

- [x] 1. Mark Increment 4 and 5 tasks `[x]` in `spec/tasks.md` (done)
- [x] 2. Add optional `loop:` block (commented out, with defaults documented)
         to `examples/toy-app/sunwell.yml`
- [x] 3. Rewrite `.claude/skills/loop/SKILL.md` — full state-machine orchestration
         playbook
- [x] 4. Test `/sunwell:loop --config examples/toy-app` end-to-end
- [x] 5. `git status` — confirm no untracked files

## Increment 7 — Results Path, SUCCESS Condition, Clean Skill, Loop Rename

- [ ] 1. `git mv .claude/skills/loop .claude/skills/run` — rename skill directory
- [ ] 2. Update `.claude/skills/run/SKILL.md`:
         - Change SUCCESS termination check from ANY benchmark to ALL benchmarks
         - Update setup step 4: read `{app-path}/sunwell-results/experiments.json`
         - Update all `results/` path references to `{results-dir}` (derived as
           `{app-path}/sunwell-results` in setup)
         - Update reject message to reference `/run` instead of `/sunwell:loop`
         - Update final report footer: `{results-dir}/experiments.json`
- [ ] 3. Create `.claude/skills/clean/SKILL.md` — full clean playbook:
         - Parse `--config <app-path>`; derive `results-dir`
         - Read `{results-dir}/experiments.json`; collect all unique paths from
           `files-changed` across all entries
         - Present confirmation summary: files to revert + directory to delete
         - On `confirm`: `git restore` each file; delete `{results-dir}/`
         - Report what was reverted and deleted; on cancel report nothing changed
         - Edge cases: `git restore` failure per file (continue, report); missing
           `experiments.json` (stop, nothing to clean); missing `results-dir`
           (skip deletion)
- [ ] 4. Update `.claude/skills/profile/SKILL.md`: derive `results-dir =
         {app-path}/sunwell-results`; replace all `results/` with `{results-dir}/`
         in steps 5 and 6
- [ ] 5. Update `.claude/skills/analyze/SKILL.md`: derive `results-dir`; replace
         all `results/` with `{results-dir}/` throughout all steps
- [ ] 6. Update `.claude/skills/improve/SKILL.md`: derive `results-dir`; replace
         all `results/` with `{results-dir}/` throughout both phases
- [ ] 7. Update `.claude/skills/experiment/SKILL.md`: derive `results-dir`;
         replace all `results/` with `{results-dir}/` throughout all steps
- [ ] 8. Create `examples/toy-app/.gitignore` containing `sunwell-results/`
- [ ] 9. Update repo root `.gitignore`: add `**/sunwell-results/`
- [ ] 10. Update `CLAUDE.md` repo structure: `loop/SKILL.md` → `run/SKILL.md`;
          add `clean/SKILL.md`; update results path description
- [ ] 11. Run `/clean --config examples/toy-app` against the current
          `examples/toy-app/sunwell-results/` state: verify it presents the
          confirmation summary, reverts `CpuHog.java`, and deletes the
          `sunwell-results/` directory
- [ ] 12. Run `/run --config examples/toy-app` end-to-end: verify results land
          in `examples/toy-app/sunwell-results/`, loop runs past the first
          iteration (SUCCESS now requires ALL benchmarks), and the skill is
          invoked without name collision
- [ ] 13. `git status` — confirm no untracked files

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
