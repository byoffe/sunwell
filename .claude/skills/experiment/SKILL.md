---
name: experiment
description: Apply a pending improvement from the experiment tree, run the full build-deploy-profile-analyze pipeline, record the result, and update experiments.json with the measured delta vs. baseline. Implemented in Session 5.
allowed-tools: "Bash Read Write"
---

## Experiment

**Not yet implemented — Session 5.**

This skill will:
1. Read a pending experiment from `results/experiments.json`
2. Apply the proposed code change (as a patch or direct edit)
3. Run the full loop: deploy → profile → analyze
4. Compute the delta vs. baseline (or parent experiment)
5. Write the result back to `results/experiments.json`:
   - status: `complete` or `rejected`
   - measured delta per benchmark
   - path to the `.jfr` and `analysis.md` for this experiment
6. If the experiment improves on baseline, it becomes the new parent for further experiments

The experiment tree allows Claude to track multiple branches of improvement in parallel
and report which path yielded the best results.

Report: `Experiment stage not yet available. Implement in Session 5.`
