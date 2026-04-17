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

<!-- Add more feedback below -->
