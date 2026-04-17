# Sunwell — Working Notes

> This file is temporary. It absorbs session-to-session churn.
> Permanent decisions get promoted to CLAUDE.md.
> This file gets deleted when the project matures.
>
> NOTE: WORKING.md is a recognized false start of the spec approach now
> defined in skills/spec/SKILL.md. It remains useful as a session log and
> parking lot but will not be replaced when deleted — the spec workflow
> supersedes it.

## Status

Session 2 + spec workflow detour complete. Staged, not yet committed (10 files).

## Picking Up Next Session

### Immediate (start of next session)

1. **Review unreviewed work from Session 2** — the following were written but
   not reviewed by the user yet:
   - `scripts/deploy.sh` — deploy script (confirmed working, not reviewed)
   - `skills/deploy/SKILL.md` — deploy skill
   - `skills/loop/SKILL.md` — loop orchestrator (deploy stage only for now)
   - `skills/profile/SKILL.md` — stub
   - `skills/analyze/SKILL.md` — stub
   - `skills/improve/SKILL.md` — stub
   - `skills/experiment/SKILL.md` — stub
   - `.claude-plugin/plugin.json` — plugin manifest
   - `CLAUDE.md` — updated with plugin architecture, git hygiene, spec skill sections

2. **Commit staged session 2 work** — 10 files currently staged, pending commit/push.
   Also consider adding `.gitattributes` to silence CRLF warnings before committing.

3. **Retire WORKING.md via the spec workflow** — WORKING.md's "Upcoming Sessions"
   backlog should be translated into a proper spec. The right first step:
   invoke `/sunwell:spec` and let it write `spec/requirements.md` for Session 3
   (profile stage). WORKING.md is then vestigial and can be deleted.

### Session 3 (after review + commit)

Profile stage — to be specced via `/sunwell:spec` before any implementation:
- `scripts/profile.sh` — SSH in, launch JMH with JFR flags, collect .jfr
- `skills/profile/SKILL.md` — flesh out the stub
- Save recording to `results/<run-id>/recording.jfr`
- Update `results/experiments.json` with baseline entry

## Open Questions / Parking Lot

- What does a perf target YAML actually look like? (design when needed)
- async-profiler binary delivery mechanism (scp for now, Ansible later)
- When do examples/ move to their own repo?
- Extract spec workflow to a personal skill (~/.claude/skills/) when a
  second project needs it — not before
- `.gitattributes` to fix CRLF warnings on Windows (fold into next commit)

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

### Session 2 — Complete (staged, not committed)
- [x] Pivoted from flat Claude commands to skill plugin architecture
- [x] `.claude-plugin/plugin.json` (name: sunwell)
- [x] `scripts/deploy.sh` — confirmed working against Docker target
- [x] `skills/deploy/SKILL.md` (disable-model-invocation)
- [x] `skills/loop/SKILL.md` (orchestrator, context:fork, deploy stage only)
- [x] Stub skills: profile, analyze, improve, experiment
- [x] CLAUDE.md updated (plugin architecture, experiment tree, git hygiene, vocabulary)

### Session 2 Detour — Spec Workflow (committed)
- [x] Adopted Kiro-style three-artifact spec structure (requirements/design/tasks)
- [x] `spec/` directory (singular; one active spec per branch; empty on main)
- [x] Spec lifecycle: draft → approved → in-progress → complete → compress → delete
- [x] `skills/spec/SKILL.md` — spec workflow as a runnable, project-agnostic skill
- [x] Committed + pushed: `50b1124`
