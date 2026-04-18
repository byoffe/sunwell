---
name: clean
description: Resets the app to a clean state by reverting all experiment-changed source files via git restore and deleting the sunwell-results directory. Requires explicit confirmation before any destructive action.
when_to_use: When the user asks to clean up, reset, or undo Sunwell experiment changes for an app.
argument-hint: "[--config <app-path>]"
allowed-tools: "Bash(git restore *) Bash(rm -rf *) Read"
---

## Clean

Reverts all source file changes recorded in `experiments.json` and deletes
the `sunwell-results/` directory for an app. Requires explicit confirmation
before any destructive action is taken.

**Usage:** `/clean [--config <app-path>]`

- `--config <app-path>` — directory containing `sunwell.yml`; defaults to `.`.
  For the toy-app during development, pass `examples/toy-app`.

---

### Steps

**1. Parse args and derive paths**

Parse `$ARGUMENTS`:
- `--config <app-path>` → use that directory; default to `.`

Derive: `results-dir = {app-path}/sunwell-results`

**2. Read state**

Read `{results-dir}/experiments.json`. If the file does not exist, stop:
> "Nothing to clean — `{results-dir}/experiments.json` not found."

Collect all unique file paths from the `files-changed` arrays across all
entries. Deduplicate. If all `files-changed` arrays are empty or absent,
note that no source files were modified by experiments.

Count the number of subdirectories in `{results-dir}/` to show in the summary.

**3. Present confirmation summary**

```
Sunwell Clean — what will happen:

  Revert {N} source file(s) via git restore:
    {file1}
    {file2}
    ...
  (none — no source files were modified by experiments)  ← if N=0

  Delete: {results-dir}/

Type 'confirm' to proceed, anything else to cancel.
```

**Stop. Do not revert or delete anything yet.**

**4. On confirm**

For each file in the collected set:

```!
git restore {file}
```

Report each result inline (success or failure). If `git restore` fails for
a file (not tracked by git, file does not exist, etc.), report the failure
and continue with the remaining files — do not abort.

Then delete the results directory:

```!
rm -rf {results-dir}
```

**5. Report**

```
Clean complete.
  Reverted: {N} file(s)
    {file1} — reverted
    {file2} — failed: {reason}
  Deleted:  {results-dir}/
Working tree is clean.
```

On cancel (anything other than `confirm`): "Clean cancelled. Nothing was changed."

---

### Edge Cases

- `experiments.json` absent → stop: "Nothing to clean — `{results-dir}/experiments.json` not found."
- `results-dir` does not exist → skip deletion step; note "no results directory found" in report
- A file in `files-changed` no longer exists on disk → skip `git restore` for that file; note in report
- Developer runs clean mid-loop (improvement approved but experiment not yet run)
  → clean reverts the improvement; on re-invoke, loop resumes at IMPROVE_PROPOSE
  and regenerates the proposal from the restored baseline analysis
