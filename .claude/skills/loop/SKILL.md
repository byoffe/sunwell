---
name: loop
description: Run the full Sunwell performance engineering loop — deploy, profile, collect, analyze, propose improvements, and track experiments. Reads sunwell.yml for app and target config. Invoke this to drive an autonomous tuning session.
context: fork
agent: general-purpose
allowed-tools: "Bash Read Write"
---

# Sunwell Loop

You are running the Sunwell performance engineering loop. Drive each stage in
sequence, maintain the experiment tree, and report clearly after every stage.

## Arguments

Parse from `$ARGUMENTS`:
- `--target <name>` — deploy target (default: `default-target` in `sunwell.yml`)
- `--focus <focus>` — profiling focus (default: `default-focus` in `sunwell.yml`)
- `--config <path>` — path to `sunwell.yml` (default: `examples/toy-app/sunwell.yml`)

## Setup

Read the config file (default `examples/toy-app/sunwell.yml`). Extract target
and focus using the same resolution order as the individual skills:
- target: `--target` arg → `default-target` → stop with error
- focus: `--focus` arg → `default-focus` → `baseline`

Read `results/experiments.json` if it exists. If it does not exist this is
a baseline run — note that; the file will be created by the profile stage.

## Loop Stages

Report `[STAGE N/5] <name> — <status>` before and after each stage.

---

### Stage 1 — Deploy

Build the JAR and deploy to target. Read target config from `sunwell.yml`.

```!
mvn package -pl {maven.module} --also-make -q
```

```!
bash .claude/skills/deploy/deploy-ssh.sh \
  {host} {port} {user} {key} {jar} {remote-path}
```

If deploy fails, stop and report. Do not proceed to profiling.

---

### Stage 2 — Profile + Collect

SSH in, run with profiler attached, collect recording locally.

```!
bash .claude/skills/profile/profile-jfr.sh \
  {host} {port} {user} {key} {remote-path} {jar-filename} {duration} {run-id}
```

```!
bash .claude/skills/profile/collect-ssh.sh \
  {host} {port} {user} {key} /tmp/{run-id}.jfr results/{run-id}
```

Write the `experiments.json` entry after collect succeeds.

If either script fails, stop and report.

---

### Stage 3 — Analyze

**Not yet implemented.**

Report: `[STAGE 3/5] Analyze — pending`

---

### Stage 4 — Improve

**Not yet implemented.**

Report: `[STAGE 4/5] Improve — pending`

---

### Stage 5 — Experiment

**Not yet implemented.**

Report: `[STAGE 5/5] Experiment — pending`

---

## End of Loop

```
Sunwell Loop Complete
─────────────────────────────────────────────
✓ Deploy       — {target}
✓ Profile      — {focus} / {profiler} / {run-id}
○ Analyze      — pending
○ Improve      — pending
○ Experiment   — pending

Recording: results/{run-id}/recording.jfr
Experiments: results/experiments.json
```
