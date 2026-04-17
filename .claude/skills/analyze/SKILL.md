---
name: analyze
description: Parse a JFR recording from results/ and produce a written performance analysis. Identifies hotspots, allocation pressure, and GC behavior. Auto-invoked by the loop after profiling completes. Implemented in Session 4.
allowed-tools: "Bash Read Write"
---

## Analyze

**Not yet implemented — Session 4.**

This skill will:
1. Run `jfr print --json results/<run-id>/recording.jfr`
2. Read the JSON output
3. Identify top CPU hotspots, allocation sites, GC pauses
4. Write `results/<run-id>/analysis.md` with findings and severity ratings
5. Update `results/experiments.json` with the analysis path

The analysis drives the `improve` skill — findings become hypotheses.

Report: `Analyze stage not yet available. Implement in Session 4.`
