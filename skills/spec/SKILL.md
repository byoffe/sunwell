---
name: spec
description: Guide the three-phase spec workflow for a new feature or session goal. Writes spec/requirements.md, spec/design.md, and spec/tasks.md in sequence, stopping after each for review. Claude auto-invokes when planning language is detected; also user-invocable directly.
when_to_use: When the user asks to plan, design, spec, or start work on a new feature, session, or non-trivial change.
allowed-tools: "Read Write"
---

# Spec Workflow

You are running the spec workflow. Your job is to guide a feature from
idea to approved implementation plan, one artifact at a time.

**Never write code or modify non-spec files during this skill.**

---

## Detect the Current Phase

Check what already exists in `spec/`:

- No files → start with **Phase 1 (Requirements)**
- `requirements.md` exists, no `design.md` → start with **Phase 2 (Design)**
- `design.md` exists, no `tasks.md` → start with **Phase 3 (Tasks)**
- `tasks.md` exists → spec is complete; remind the user to say "implement" to proceed,
  or "compress and close" if implementation is done

If `$ARGUMENTS` names a phase explicitly (`requirements`, `design`, or `tasks`),
jump to that phase regardless.

---

## Phase 1 — Requirements

Write `spec/requirements.md`:

```markdown
# Requirements: <feature name>

## Problem
<What is broken, missing, or inefficient? Why does it matter?>

## Goals
<What does "done" look like from the user's perspective?>

## Acceptance Criteria
- [ ] <Testable, specific criterion>
- [ ] <Testable, specific criterion>

## Out of Scope
<What this spec explicitly does not address>
```

After writing, tell the user:
- What you wrote and why each section matters
- **"Review spec/requirements.md. When it looks right, say 'requirements look good' and I'll write the design."**

Stop. Do not write design.md.

---

## Phase 2 — Design

Read `spec/requirements.md` first. Write `spec/design.md`:

```markdown
# Design: <feature name>

## Approach
<The chosen technical approach and why. One paragraph.>

## Key Decisions
| Decision | Choice | Rationale |
|---|---|---|
| <what> | <chosen option> | <why this over alternatives> |

## File and Component Changes
| File | Change |
|---|---|
| `path/to/file` | <what changes and why> |

## Edge Cases and Failure Modes
- <What can go wrong and how it's handled>

## Deferred
<Anything explicitly out of scope for this spec, to be addressed later>
```

After writing, tell the user:
- The key decisions made and any alternatives considered
- **"Review spec/design.md. When it looks right, say 'approve design' and I'll write the tasks."**

Stop. Do not write tasks.md.

---

## Phase 3 — Tasks

Read `spec/requirements.md` and `spec/design.md` first. Write `spec/tasks.md`:

```markdown
status: draft

# Tasks: <feature name>

- [ ] 1. <Specific, concrete action — one file or one concern>
- [ ] 2. <Next action>
- [ ] 3. ...

## Notes
<Anything the implementer should know that isn't in the design>
```

Rules for tasks:
- Each task is one concrete action (create a file, add a method, update a config)
- Tasks execute design decisions — they do not make them
- If writing a task requires a design decision, stop and flag it as a design gap

After writing, tell the user:
- A quick summary of the task sequence and estimated scope
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

## Compress and Close

When the user says "compress and close":

1. Read `spec/design.md` and the implemented code
2. Identify any design decisions that are non-obvious from the code alone
3. For each one, propose where it should be memorialized:
   - Language-appropriate inline documentation (e.g., `package-info.java` for Java,
     module docstrings for Python) — architectural rationale, non-obvious invariants
   - The project's permanent conventions document (e.g., `CLAUDE.md`, ADR log, or
     equivalent) — project-wide conventions that emerged from this feature
   - Git commit body — the "why this over X" narrative
4. Write those additions (to code files, not spec files)
5. Delete `spec/requirements.md`, `spec/design.md`, `spec/tasks.md`
6. Report: "Spec closed. [N] decisions memorialized in [locations]. spec/ is clean."

**The spec folder should be empty after this step.**
