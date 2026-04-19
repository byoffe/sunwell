---
name: profile
description: Profiles the app on a configured SSH target using a focus-derived JMH profiler config, collects all JFR recordings locally, and writes an experiments.json entry.
when_to_use: When the user asks to profile, benchmark, or collect a JFR recording from a target host.
argument-hint: "[--config <app-path>] [target] [--focus <focus>]"
allowed-tools: "Bash(date -u) Bash(bash .claude/skills/profile/profile-jfr.sh) Bash(bash .claude/skills/profile/collect-ssh.sh) Read Write"
---

## Profile

Profiles the app on the named target using the focus-derived profiler config.
Collect is folded into this skill — the recording lands locally before this
skill exits.

**Usage:** `/profile [--config <app-path>] [target] [--focus <focus>]`

- `--config <app-path>` — directory containing `sunwell.yml`; defaults to `.`
  (current working directory). For the toy-app during development, pass
  `examples/toy-app`.
- `target` — optional; overrides `default-target` in `sunwell.yml`
- `--focus` — optional; overrides `default-focus` in `sunwell.yml`

---

### Steps

**1. Read `sunwell.yml`**

Parse `$ARGUMENTS`:
- `--config <app-path>` → use that directory; default to `.`
- `--focus <focus>` → focus override
- remaining first non-flag token → target name override

Read `{app-path}/sunwell.yml`. Extract:
- `jar` — path to the JAR filename (basename only needed for remote)
- `default-target`, `default-focus`
- Named target block: `host`, `port`, `user`, `key`, `remote-path`

Resolve target: CLI target arg → `default-target`.
Resolve focus: `--focus` arg → `default-focus` → `baseline`.

If the target does not exist in `sunwell.yml`, stop and list available targets.
If the focus is not a recognized value, stop and list valid focus values.

Derive: `results-dir = {app-path}/sunwell-results`

**2. Resolve focus to profiler config**

Use the built-in defaults table. Apply any `profile.overrides.<focus>` from
`sunwell.yml` on top. Duration is no longer passed to the script — JMH manages
recording lifecycle via `-prof jfr`.

| Focus | Profiler | JMH profiler flag |
|---|---|---|
| `baseline` | JFR | `-prof "jfr:dir=/tmp/<run-id>"` |
| `gc` | JFR | `-prof "jfr:dir=/tmp/<run-id>"` |
| `cpu` | async-profiler | *(async-profiler not yet available — see note below)* |
| `memory` | async-profiler | *(async-profiler not yet available — see note below)* |
| `lock` | async-profiler | *(async-profiler not yet available — see note below)* |

**async-profiler focuses:** if `cpu`, `memory`, or `lock` is requested, stop
and report: "async-profiler is not yet configured for this target. Use
`--focus baseline` or `--focus gc` to proceed with JFR." Do not silently
fall back.

**3. Generate run-id**

```!
date -u +%Y%m%d-%H%M%S
```

Use the output as `<run-id>` (e.g., `20260416-143012`).

**4. Run the profile script**

```!
bash .claude/skills/profile/profile-jfr.sh \
  {host} {port} {user} {key} {remote-path} {jar-filename} {run-id}
```

JMH writes per-benchmark recordings to `/tmp/{run-id}/<benchmark>-<mode>/profile.jfr`
on the remote. If the script exits non-zero, report the error and stop. Do not retry.

**5. Collect the recordings**

```!
bash .claude/skills/profile/collect-ssh.sh \
  {host} {port} {user} {key} /tmp/{run-id} {results-dir}/{run-id}
```

Copies the entire remote directory (all benchmark subdirectories) to `{results-dir}/{run-id}/`.
If collect fails (no .jfr files found on remote), report the path searched and stop.

**6. Write experiments.json entry**

Read `{results-dir}/experiments.json` if it exists; create it if not.

Append this entry:

```json
{
  "run-id": "<run-id>",
  "timestamp": "<ISO-8601 UTC>",
  "target": "<target-name>",
  "focus": "<focus>",
  "profiler": "<jfr|async-profiler>",
  "artifact-path": "{results-dir}/<run-id>/",
  "analysis-path": null,
  "hypothesis": null,
  "suggested-next-focus": null,
  "files-changed": [],
  "delta": null,
  "proposal-path": null,
  "improvement-status": null,
  "parent-run-id": null
}
```

Write the updated JSON back to `{results-dir}/experiments.json` using the
Write tool — do not use Bash or a heredoc.

**7. Report**

Confirm:
- Run-id, target, focus, profiler used
- Recording path and size
- Ready for analyze stage
