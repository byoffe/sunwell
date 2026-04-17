---
name: loop
description: Run the full Sunwell performance engineering loop — build, deploy, profile, collect, analyze, propose improvements, and track experiments. Invoke this to drive an autonomous tuning session. Pass --auto to skip manual review prompts.
context: fork
agent: general-purpose
allowed-tools: "Bash Read Write"
---

# Sunwell Loop

You are running the Sunwell performance engineering loop. Your job is to drive each
stage in sequence, maintain the experiment tree, and report clearly after every stage.

## Arguments

`$ARGUMENTS` may include:
- `--auto` — skip user review prompts; apply improvements and run experiments automatically
- `--target <name>` — deploy target (default: `local-docker`)

Parse these from `$ARGUMENTS` at the start.

## Experiment Tree

The experiment tree lives at `results/experiments.json`. It is gitignored and
local to this workstation. Read it at the start of every loop run.

If it does not exist, this is a **baseline run** — create the file with an empty
baseline entry after the profile stage completes.

```json
{
  "baseline": null,
  "experiments": []
}
```

## Loop Stages

Work through each stage in order. Report `[STAGE N/6] <name> — <status>` before
and after each one so the user can follow progress.

---

### Stage 1 — Deploy

Run the deploy script:

```bash
bash scripts/deploy.sh ${target:-local-docker}
```

If it fails, stop and report the error. Do not proceed to profiling.

---

### Stage 2 — Profile

**Not yet implemented (Session 3).**

Report: `[STAGE 2/6] Profile — pending (Session 3)`

Stop here for now and summarize what was accomplished.

---

### Stage 3 — Collect

**Not yet implemented (Session 3).**

Report: `[STAGE 3/6] Collect — pending (Session 3)`

---

### Stage 4 — Analyze

**Not yet implemented (Session 4).**

Report: `[STAGE 4/6] Analyze — pending (Session 4)`

---

### Stage 5 — Improve

**Not yet implemented (Session 4/5).**

Report: `[STAGE 5/6] Improve — pending (Session 4/5)`

---

### Stage 6 — Experiment

**Not yet implemented (Session 5).**

Report: `[STAGE 6/6] Experiment — pending (Session 5)`

---

## End of Loop

After completing all available stages, write a summary:

```
Sunwell Loop Complete
─────────────────────────────────
✓ Deploy       — success
○ Profile      — pending (Session 3)
○ Collect      — pending (Session 3)
○ Analyze      — pending (Session 4)
○ Improve      — pending (Session 4/5)
○ Experiment   — pending (Session 5)

Experiment tree: results/experiments.json
Next: run /sunwell:loop again after Session 3 is complete.
```
