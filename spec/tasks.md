# Tasks: Sunwell — Full Loop

## Increment 1 — Configuration + Deploy + Profile + Collect

- [ ] 1. Create `examples/toy-app/sunwell.yml` with app config, `local-docker`
         target, and `default-focus: baseline`
- [ ] 2. `git mv scripts/deploy.sh .claude/skills/deploy/deploy-ssh.sh`
- [ ] 3. Rewrite `deploy-ssh.sh` to accept all config as positional args
         (`<host> <port> <user> <key> <local-jar> <remote-path>`); remove all
         hardcoded values and the target `case` statement
- [ ] 4. Update `.claude/skills/deploy/SKILL.md` to read `sunwell.yml`, resolve
         the named target, and call `deploy-ssh.sh` with fully-resolved args
- [ ] 5. Create `.claude/skills/profile/profile-jfr.sh` — SSHes in, runs JAR
         with JFR flags, waits for completion; leaves recording at
         `/tmp/<run-id>.jfr` on the remote host; accepts
         `<host> <port> <user> <key> <remote-path> <jar-filename> <duration> <run-id>`
- [ ] 6. Create `.claude/skills/profile/collect-ssh.sh` — SCPs
         `/tmp/<run-id>.jfr` from remote to `results/<run-id>/recording.jfr`
         locally; creates `results/<run-id>/` if absent; accepts
         `<host> <port> <user> <key> <remote-file> <local-dir>`
- [ ] 7. Flesh out `.claude/skills/profile/SKILL.md`: read `sunwell.yml`,
         resolve focus (CLI arg → `default-focus` → `baseline`), apply any
         `profile.overrides`, call `profile-jfr.sh` then `collect-ssh.sh`,
         write the `experiments.json` entry with nulls for analyze/improve fields
- [ ] 8. Update `.claude/skills/loop/SKILL.md` to run deploy → profile in
         sequence, reading target and focus from `sunwell.yml`
- [ ] 9. Update `CLAUDE.md` repo structure: remove `scripts/` entry, add
         co-located scripts under each skill directory
- [ ] 10. Run `git status` — confirm no untracked files; `git add` or
          `.gitignore` anything that floats

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
