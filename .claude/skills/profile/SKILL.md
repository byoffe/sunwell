---
name: profile
description: Profiles the app on a configured SSH target. Detects async-profiler availability and routes each focus to the preferred profiler (async-profiler for cpu/memory/lock, JFR for baseline/gc), with JFR fallback. Collects recordings locally and writes an experiments.json entry.
when_to_use: When the user asks to profile, benchmark, or collect a JFR recording from a target host.
argument-hint: "[--config <app-path>] [target] [--focus <focus>]"
allowed-tools: "Bash(date -u) Bash(ssh *) Bash(bash .claude/skills/profile/profile-run.sh) Bash(bash .claude/skills/profile/collect-ssh.sh) Read Write"
---

## Profile

Profiles the app on the named target using the preferred profiler for the
requested focus. Collect is folded into this skill — the recording lands
locally before this skill exits.

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
- `jar` — path to the JAR (basename only needed for remote)
- `default-target`, `default-focus`
- Named target block: `host`, `port`, `user`, `key`, `remote-path`
- `profile.profiler-override` — optional map of focus → profiler name

Resolve target: CLI target arg → `default-target`.
Resolve focus: `--focus` arg → `default-focus` → `baseline`.

Valid focus values: `baseline`, `gc`, `cpu`, `memory`, `lock`.

If the target does not exist in `sunwell.yml`, stop and list available targets.
If the focus is not a recognized value, stop and list valid focus values.

Derive: `results-dir = {app-path}/sunwell-results`

**2. Detect async-profiler and resolve profiler**

**Step 2a — Check for profiler-override:**

If `profile.profiler-override.<focus>` is set in `sunwell.yml`, use that
profiler. Skip the detection probe. Log:
`"profiler override: using <profiler> for focus <focus>"`

If the override specifies `async-profiler` but detection (below) finds it is
not installed, stop and report the conflict. Do not silently fall back — the
operator explicitly requested it.

**Step 2b — Apply built-in defaults (no override):**

`baseline` and `gc` always use JFR. Skip detection. Set `profiler = jfr`.

For `cpu`, `memory`, and `lock`: run the detection probe:

```!
ssh -i {key} -p {port} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes {user}@{host} \
  "test -f /opt/async-profiler/lib/libasyncProfiler.so && echo found || echo not-found"
```

- Output `found` → `profiler = async-profiler`. Log: `"using async-profiler (available at /opt/async-profiler)"`
- Output `not-found` → `profiler = jfr`. Log: `"async-profiler not found on <target>; falling back to JFR"`

**Step 2c — Build the JMH profiler flag:**

| Focus | Profiler | JMH profiler flag |
|---|---|---|
| `baseline` | JFR | `jfr:dir=/tmp/<run-id>` |
| `gc` | JFR | `jfr:dir=/tmp/<run-id>` |
| `cpu` | async-profiler | `async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=cpu;output=jfr;dir=/tmp/<run-id>` |
| `cpu` | JFR (fallback) | `jfr:dir=/tmp/<run-id>` |
| `memory` | async-profiler | `async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=alloc;output=jfr;dir=/tmp/<run-id>` |
| `memory` | JFR (fallback) | `jfr:dir=/tmp/<run-id>` |
| `lock` | async-profiler | `async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=lock;output=jfr;dir=/tmp/<run-id>` |
| `lock` | JFR (fallback) | `jfr:dir=/tmp/<run-id>` |

Note: when `lock` focus uses async-profiler, the recording is collected
but the analyze skill has no lock dimension yet. Log:
`"lock analysis not yet available; recording collected for future use."`

**3. Generate run-id**

```!
date -u +%Y%m%d-%H%M%S
```

Use the output as `<run-id>` (e.g., `20260416-143012`).

**4. Run the profile script**

```!
bash .claude/skills/profile/profile-run.sh \
  {host} {port} {user} {key} {remote-path} {jar-filename} {run-id} "{profiler-flag}"
```

If the script exits non-zero, report the error and stop. Do not retry.

**5. Collect the recordings**

```!
bash .claude/skills/profile/collect-ssh.sh \
  {host} {port} {user} {key} /tmp/{run-id} {results-dir}/{run-id}
```

Copies the entire remote directory (all benchmark subdirectories) to
`{results-dir}/{run-id}/`. If collect fails (no `.jfr` files found on
remote), report the path searched and stop.

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
- Run-id, target, focus, profiler used (and why: detected / fallback / override)
- Recording path and size
- Ready for analyze stage
