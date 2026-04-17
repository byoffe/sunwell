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

- [x] 11. SSH into the Docker target and run a minimal JMH benchmark with
          `-prof jfr` to confirm where JMH writes per-fork JFR files; document
          the output path in design.md before touching any scripts
- [x] 12. Update `profile-jfr.sh` to pass `-prof jfr` to JMH instead of
          `-XX:StartFlightRecording=...`; remove the `<duration>` positional arg
- [x] 13. Update `collect-ssh.sh` to SCP all `*.jfr` files from the JMH output
          directory rather than a single `/tmp/<run-id>.jfr`
- [x] 14. Update `profile/SKILL.md` steps 4 and 5 to reflect the new script
          interfaces
- [x] 15. Run a live profile + collect to confirm all per-fork recordings land
          in `results/<run-id>/`
- [x] 16. Run `git status` — confirm no untracked files

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
