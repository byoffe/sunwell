---
name: profile
description: SSH into the target server and run the toy-app JMH benchmarks with JFR or async-profiler attached. Collects the recording and saves it to results/. Implemented in Session 3.
disable-model-invocation: true
allowed-tools: "Bash Read Write"
---

## Profile

**Not yet implemented — Session 3.**

This skill will:
1. SSH into the target server
2. Launch `toy-app-benchmarks.jar` with JFR flags (`-XX:StartFlightRecording=...`)
3. Run the JMH suite
4. Collect the `.jfr` recording back to `results/<run-id>/recording.jfr`

Profiler selection follows the goal:

| Goal | Profiler | Flags |
|---|---|---|
| CPU hotspots | JFR | `-XX:StartFlightRecording=event=cpu` |
| Allocation pressure | JFR | `-XX:StartFlightRecording=event=allocation` |
| Full baseline | JFR | `-XX:StartFlightRecording=settings=profile` |

Report: `Profile stage not yet available. Implement in Session 3.`
