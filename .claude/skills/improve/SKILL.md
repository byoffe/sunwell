---
name: improve
description: Read the latest performance analysis and propose concrete code improvements ranked by expected impact. Each improvement becomes an experiment candidate. User can review proposals or pass --auto to apply all. Implemented in Session 4/5.
allowed-tools: "Bash Read Write"
---

## Improve

**Not yet implemented — Session 4/5.**

This skill will:
1. Read `results/<run-id>/analysis.md`
2. Propose concrete code changes ranked by expected impact
3. Present proposals to the user (unless `--auto` was passed to the loop)
4. Write accepted proposals as pending experiments to `results/experiments.json`

Each proposal includes:
- **Hypothesis** — what the change is and why it should help
- **Files affected** — specific Java classes and line ranges
- **Expected delta** — rough throughput or allocation improvement estimate

The `experiment` skill runs each accepted proposal through the full loop.

Report: `Improve stage not yet available. Implement in Session 4/5.`
