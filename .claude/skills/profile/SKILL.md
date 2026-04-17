---
name: profile
description: Read sunwell.yml, resolve focus to profiler flags, SSH into the target, run the JMH benchmark with the appropriate profiler attached, collect the recording, and write an experiments.json entry.
allowed-tools: "Bash Read Write"
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
  {host} {port} {user} {key} /tmp/{run-id} results/{run-id}
```

Copies the entire remote directory (all benchmark subdirectories) to `results/{run-id}/`.
If collect fails (no .jfr files found on remote), report the path searched and stop.

**6. Write experiments.json entry**

Read `results/experiments.json` if it exists; create it if not.

Append this entry:

```json
{
  "run-id": "<run-id>",
  "timestamp": "<ISO-8601 UTC>",
  "target": "<target-name>",
  "focus": "<focus>",
  "profiler": "<jfr|async-profiler>",
  "artifact-path": "results/<run-id>/",
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

Write the updated JSON back to `results/experiments.json`.

**7. Report**

Confirm:
- Run-id, target, focus, profiler used
- Recording path and size
- Ready for analyze stage
