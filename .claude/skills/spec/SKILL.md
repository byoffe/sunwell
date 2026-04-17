---
name: spec
description: Guide the three-phase spec workflow for a new feature. Requirements covers the full vision and persists. Design and tasks are cumulative living documents — each increment appends to them. Compression is optional maintenance, not a post-increment ritual. Claude auto-invokes when planning language is detected; also user-invocable directly.
when_to_use: When the user asks to plan, design, spec, or start work on a new feature or non-trivial change.
allowed-tools: "Read Write"
---

# Spec Workflow

You are running the spec workflow. Your job is to guide a feature from
idea to approved implementation plan, one artifact at a time.

**Never write code or modify non-spec files during this skill.**

## Granularity Model

The three artifacts operate at different granularities and have different
lifetimes:

- **`requirements.md`** — the **full feature vision**. Every stage, every goal,
  every acceptance criterion. Can be large. Written once and revised as
  understanding evolves. Persists until every acceptance criterion is satisfied.
  This is the anchor for all design decisions.

- **`design.md`** — a **cumulative, living design record**. Starts with the
  first increment's decisions. Each subsequent increment *appends* a new
  section — it does not replace the previous one. Grows over time. Contains
  the full rationale for every decision made so far. Never deleted mid-spec.

- **`tasks.md`** — a **cumulative task history**. Completed tasks are checked
  off but remain visible. Each increment appends new tasks. The full list shows
  what was built, in what order, and what comes next. Never deleted mid-spec.

Compression is an *optional maintenance operation* — triggered when the
documents get unwieldy, not as a ritual after each increment. Deletion happens
only when all requirements are satisfied.

---

## feedback.md

`spec/feedback.md` is a free-form document for capturing questions, concerns,
and observations on work in progress — including work already implemented but
not yet reviewed. It is not a phase artifact; it can exist at any time alongside
requirements/design/tasks. When the user says "capture this feedback" or
"note this", append it to `spec/feedback.md`. At compress-and-close, process
any open items into requirements changes, design revisions, or task additions,
then delete the file.

---

## Detect the Current Phase

Check what already exists in `spec/`:

- No files → start with **Phase 1 (Requirements)**
- `requirements.md` exists, no `design.md` → start with **Phase 2 (Design)**
- `design.md` exists, no `tasks.md` → start with **Phase 3 (Tasks)**
- `tasks.md` exists → in progress; ask which increment is next, or wait for
  "compress" or "close"

If `$ARGUMENTS` names a phase explicitly (`requirements`, `design`, or `tasks`),
jump to that phase regardless.

---

## Phase 1 — Requirements

Write `spec/requirements.md`. This document covers the **full feature vision**
— every stage, every goal, every acceptance criterion. It can be long. Do not
artificially limit scope to what fits in one design/tasks cycle.

```markdown
# Requirements: <feature name>

## Problem
<What is broken, missing, or inefficient? Why does it matter?>

## Vision
<The end state in plain language — what the system does when fully built.
 One or two paragraphs. This is the anchor for all design decisions.>

## Goals
<Numbered list of capabilities the system must have when done.
 Can be extensive — cover every stage of the feature.>

## Acceptance Criteria
<Grouped by stage or concern. Each criterion is testable and specific.
 Check boxes remain open until that criterion is actually satisfied.>

### <Stage or Concern>
- [ ] <Testable, specific criterion>
- [ ] <Testable, specific criterion>

## Out of Scope
<What this spec explicitly does not address — be specific.>
```

After writing, tell the user:
- What you wrote and why each section matters
- **"Review spec/requirements.md. When it looks right, say 'requirements look good' and I'll write the design."**

Stop. Do not write design.md.

---

## Phase 2 — Design

Read `spec/requirements.md` first. Identify which increment is ready to design
— the next well-understood slice. Do not design the entire requirements at once.

**If `design.md` already exists**, append a new section for this increment.
Do not overwrite existing content.

**If `design.md` does not exist**, create it.

```markdown
# Design: <feature name>

## Increment 1 — <scope, e.g. "Configuration + Deploy/Profile">

### Scope
<Which acceptance criteria from requirements.md this increment addresses.>

### Approach
<The chosen technical approach and why. One paragraph.>

### Key Decisions
| Decision | Choice | Rationale |
|---|---|---|
| <what> | <chosen option> | <why this over alternatives> |

### File and Component Changes
| File | Change |
|---|---|
| `path/to/file` | <what changes and why> |

### Edge Cases and Failure Modes
- <What can go wrong and how it's handled>

### Deferred to Later Increments
<Requirements criteria explicitly not addressed here.>
```

Each subsequent increment appends another `## Increment N` section below the
previous ones. Completed increment sections are never removed — they are the
permanent record of why the system is shaped the way it is.

After writing, tell the user:
- The key decisions made and any alternatives considered
- **"Review spec/design.md. When it looks right, say 'approve design' and I'll write the tasks."**

Stop. Do not write tasks.md.

---

## Phase 3 — Tasks

Read `spec/requirements.md` and `spec/design.md` first.

**If `tasks.md` already exists**, append new tasks for this increment below the
existing ones. Do not remove or renumber completed tasks.

**If `tasks.md` does not exist**, create it.

```markdown
# Tasks: <feature name>

## Increment 1 — <scope>

- [x] 1. <Completed task>
- [ ] 2. <Pending task>

## Increment 2 — <scope>

- [ ] 3. <Next concrete action>
- [ ] 4. ...

## Notes
<Anything the implementer should know that isn't in the design>
```

Rules for tasks:
- Each task is one concrete action (create a file, add a method, update a config)
- Tasks execute design decisions — they do not make them
- Completed tasks stay in the list, checked off — they are part of the record
- If writing a task requires a design decision, stop and flag it as a design gap

After writing, tell the user:
- A quick summary of the new tasks and how they connect to prior work
- **"Review spec/tasks.md. When it looks right, say 'implement' and I'll build it."**

Stop. Do not implement.

---

## Design Gate (during implementation)

If the user is mid-implementation and surfaces a gap — something the design
didn't cover — say:

> "Design gap: [describe the unresolved question]. Options: [A] or [B].
> Should I update spec/design.md before continuing?"

Wait for direction. Do not pick an answer silently.

---

## Increment Complete (bookkeeping)

When all tasks for an increment are checked off in `tasks.md`, before committing:

1. **Check off tasks in `tasks.md`** — all tasks for the increment should be `[x]`
2. **Tick acceptance criteria in `requirements.md`** — for each criterion that is
   now satisfied, change `[ ]` to `[x]`. Only tick criteria that are actually
   met by working, committed code — not "close enough" or "mostly done".
3. **Commit** — spec files and implementation files together in one commit so the
   state of the spec always matches the state of the code.

This is not optional. requirements.md is the source of truth for what is done.
If the checkboxes don't reflect reality, the spec is lying.

---

## Compress (optional maintenance)

When `design.md` or `tasks.md` becomes unwieldy — too large to read as a
coherent document — compress it:

1. Read `spec/design.md` and the implemented code for completed increments
2. Identify decisions in completed increment sections that are non-obvious
   from the code alone
3. Memorialize those decisions:
   - Language-appropriate inline documentation — architectural rationale,
     non-obvious invariants
   - The project's permanent conventions document (`CLAUDE.md`, ADR log, etc.)
     — project-wide conventions that emerged from those increments
   - Git commit body — the "why this over X" narrative
4. Write those additions (to code files, not spec files)
5. Collapse completed increment sections into a brief summary block:
   ```markdown
   ## Increments 1–N (summarized — decisions memorialized in code/CLAUDE.md)
   <One paragraph: what was built and the key architectural choices made.>
   ```
6. In `tasks.md`, collapse completed increment task lists similarly
7. Report: "Compressed increments 1–N. [M] decisions memorialized in
   [locations]. Active increment [N+1] remains intact."

**Compress does not delete any spec file. It only condenses completed history.**

---

## Close

When every checkbox in `requirements.md` is checked:

1. Run a final compress (steps 1–6 above) if there are un-memorialized decisions
2. Check off any remaining acceptance criteria in `requirements.md`
3. Delete `spec/requirements.md`, `spec/design.md`, `spec/tasks.md`,
   and `spec/feedback.md` if it exists
4. Report: "Spec closed. [N] decisions memorialized in [locations]. spec/ is clean."

**The spec folder should be empty only after all requirements are satisfied.**
