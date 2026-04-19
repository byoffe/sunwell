---
name: improve
description: Proposes one targeted code change based on profiling analysis, gates implementation on explicit developer approval, and records the proposal and outcome in experiments.json.
when_to_use: When the user asks to improve, optimize, or propose a change based on a completed profiling analysis.
argument-hint: "[--config <app-path>] [run-id] | approve [--focus <focus>] | reject"
allowed-tools: "Read Write Edit"
---

## Improve

Reads the analysis for a profiling run, proposes one targeted code change,
and gates implementation on explicit developer approval. No source file is
modified until the developer types `approve`.

**Usage:** `/improve [--config <app-path>] [run-id]`

- `--config <app-path>` — directory containing `sunwell.yml`; defaults to `.`.
  For the toy-app during development, pass `examples/toy-app`.
- `run-id` — optional; defaults to the most recent entry in `{results-dir}/experiments.json`

---

### Phase 1 — Propose

**1. Read context**

Parse `$ARGUMENTS`:
- `--config <app-path>` → use that directory; default to `.`
- remaining first non-flag token → run-id override

**Approval fast-path:** If `$ARGUMENTS` begins with `approve` (e.g. the skill
was re-invoked with `/improve approve`), skip all of Phase 1 and jump
immediately to **Phase 2 — Implement**. Treat `$ARGUMENTS` as the approval
response (extract `--focus <override>` if present).

Derive: `results-dir = {app-path}/sunwell-results`

Read `{results-dir}/experiments.json`. Identify the target run: the named `run-id`
if provided, otherwise the most recent entry (last in the array).

If `improvement-status` is `"implemented"`, stop:
> "This run already has an implemented improvement. Run `/profile` to
> start a new loop iteration."

If `improvement-status` is `"proposed"`, a proposal already exists. Read
`{results-dir}/{run-id}/proposal.md` and re-present it with the approval prompt.
Do not generate a new proposal.

**2. Read the analysis**

Read `{results-dir}/{run-id}/analysis.md`. If not found, stop:
> "No analysis found for `<run-id>`. Run `/analyze` first."

Extract:
- The **Hypothesis** section — identifies the primary bottleneck and the
  specific method(s) responsible
- The **Suggested Next Focus** — carry this forward to the proposal

**3. Read source files**

Identify the source files and line numbers cited in the Hypothesis. Read
each file. Locate the exact methods and lines referenced.

**4. Formulate the proposal**

Select one targeted change addressing the primary bottleneck. Apply the
scope limit: one logical concern, minimum viable diff. If the analysis
identifies multiple problems, pick the highest-impact one — defer the rest.

The change must be expressible as a unified diff that a reviewer can
understand at a glance. If the diff would span more than one logical concern,
narrow it.

**5. Write `{results-dir}/{run-id}/proposal.md`**

```markdown
# Proposal: {run-id}

**Based on:** `{results-dir}/{run-id}/analysis.md`

## Change

One sentence: what is being changed and why.

## Rationale

Two to four sentences referencing specific findings from the analysis.
Cite method names, line numbers, and metrics. Explain why this change
addresses the primary bottleneck identified in the hypothesis.

## Expected Effect

What the change is expected to improve (allocation rate, CPU time, GC
pressure, etc.), and approximately by how much if a reasonable estimate
is possible from the data.

## Suggested Next Focus

{focus-name} — one sentence explaining why this focus is the right next step.

## Diff

```diff
--- a/{file}
+++ b/{file}
@@ ... @@
 context
-removed line
+added line
 context
```
```

**6. Update `experiments.json`**

Read `{results-dir}/experiments.json`. Find the entry for this `run-id`. Set:
- `proposal-path` → `"{results-dir}/{run-id}/proposal.md"`
- `improvement-status` → `"proposed"`

Write the updated JSON back using the Write tool — do not use Bash or a heredoc.

**7. Present and stop**

Print:
- The analysis hypothesis (one sentence)
- The full contents of `proposal.md`
- This prompt:

> Type `approve` to implement this change.
> Type `approve --focus <focus>` to implement and redirect the next focus.
> Type `reject` to skip this proposal.

**Stop. Do not modify any source file.**

---

### Phase 2 — Implement

Resume here when the developer responds.

**On `approve` or `approve --focus <override>`:**

1. Read `{results-dir}/experiments.json`. Find the entry for this `run-id`. Set
   `improvement-status` → `"approved"`. Write back using the Write tool.

2. Read `{results-dir}/{run-id}/proposal.md`. Extract the diff from the **Diff**
   section.

3. Apply the diff to the source file(s). Use the Edit tool — do not apply
   patches via bash.

4. If a `--focus <override>` was given, validate it against known focus values
   (`baseline`, `gc`, `cpu`, `memory`, `lock`). If invalid, list valid values
   and stop before making any changes.

5. Read `{results-dir}/experiments.json`. Update the entry:
   - `files-changed` → list of modified file paths
   - `improvement-status` → `"implemented"`
   - `suggested-next-focus` → the override if provided, otherwise leave as-is

   Write back using the Write tool — do not use Bash or a heredoc.

6. Report:
   - Files changed and lines modified
   - Suggested next focus for the next run
   - Next step: `Run /profile --config <app-path> --focus <focus> to
     measure the delta`

**On `reject`:**

1. Read `{results-dir}/experiments.json`. Find the entry for this `run-id`. Set
   `improvement-status` → `"rejected"`. Write back using the Write tool.

2. Report:
   > "Proposal rejected. Run `/improve` again to generate an alternative."

---

### Edge Cases

- `analysis.md` not found → stop: "Run `/analyze` first"
- `improvement-status: "implemented"` → stop: "Already implemented. Run profile."
- `improvement-status: "proposed"` → re-present existing proposal; do not regenerate
- Source file cited in analysis not found → report path and stop; do not guess
- `approve --focus <unknown>` → validate before applying; list valid values if unknown
- Diff conflicts with current file state → report conflict; preserve `proposal.md`;
  leave `improvement-status: "approved"`; developer resolves manually
