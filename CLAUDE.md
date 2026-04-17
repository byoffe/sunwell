# Sunwell

> *"Your code thinks it's ready. Sunwell disagrees."*

Sunwell is a Maven-based performance engineering harness for Java applications.
It closes the loop between code changes and empirical performance data, with
Claude as an active participant in every stage.

## The Loop

Build → Deploy → Instrument → Load → Collect → Analyze → Tune → Repeat

Claude orchestrates this loop via a skill plugin (`/sunwell:loop`). The loop is invariant.
What varies per application is configuration — which flags, which JAR, which
host. Not Java interfaces.

## Core Principles

> Don't define a Java interface until you have two concrete implementations
> that would otherwise duplicate code.

In early phases, the "framework" is almost entirely skill definitions and
scripts. Java code is the toy app and its JMH benchmarks. Nothing more.

Interfaces will emerge when real shared logic appears across multiple
concrete implementations. Not before.

## Repo Structure

    sunwell/
      .claude-plugin/
        plugin.json                   <- plugin manifest (name: sunwell)
      .claude/
        skills/                       <- auto-discovered project skills (temporary home)
          spec/SKILL.md               <- spec workflow (requirements→design→tasks)
          loop/SKILL.md               <- orchestrator: runs the full loop
          deploy/
            SKILL.md                  <- reads sunwell.yml, calls deploy-ssh.sh
            deploy-ssh.sh             <- transport: scp JAR + verify (no config)
          profile/
            SKILL.md                  <- resolves focus, calls profile-jfr.sh + collect-ssh.sh
            profile-jfr.sh            <- transport: SSH + JFR + wait
            collect-ssh.sh            <- transport: SCP recording home
          analyze/
            SKILL.md                  <- orchestrates scripts + subagents → analysis.md
            summarize-cpu.java        <- ExecutionSample → ranked hotspot table
            summarize-alloc.java      <- ObjectAllocationSample → ranked allocation table
            summarize-gc.java         <- GC events → pause stats, heap, allocation rate
          improve/SKILL.md            <- propose improvements from analysis
          experiment/SKILL.md         <- apply change → loop → record delta
      spec/                           <- active feature spec (one at a time; empty on main)
        .gitkeep
      harness/                        <- future home of shared Java code
        pom.xml                       <- module established, nearly empty for now
      examples/
        toy-app/                      <- bad implementations + JMH benchmarks
          src/main/java/
            CpuHog.java
            MemoryHog.java
            CpuHogBenchmark.java
            MemoryHogBenchmark.java
          pom.xml
          sunwell.yml                 <- app + target config for toy-app
        docker/
          Dockerfile
          docker-compose.yml
      results/                        <- gitignored; profiling output + experiment tree
        experiments.json              <- experiment tree (created on first loop run)
      pom.xml                         <- parent POM
      CLAUDE.md                       <- this file (permanent)
      README.md

Examples live here during early development.
Design note: extract examples to a separate repo when harness is stable.

## Key Design Decisions

### No Premature Interfaces
Profiler selection is JVM flags — configuration, not a Java interface.
Load generation is JMH annotations — convention, not a Java interface.
Deploy targets are YAML — configuration, not a Java interface.

### Profiler Selection
Claude selects profiler flags based on stated goal. No Java code involved.

| Goal | Profiler | Mechanism |
|---|---|---|
| CPU hotspots | async-profiler | -agentpath:...,event=cpu |
| Allocation pressure | async-profiler | -agentpath:...,event=alloc |
| GC behavior | JFR | -XX:StartFlightRecording=... |
| Lock contention | async-profiler | -agentpath:...,event=lock |
| Full baseline | JFR | -XX:StartFlightRecording=... |

### JFR First
Start with JFR — zero external dependencies, built into JDK 11+.
async-profiler comes later.

### Target Environment
- Local: Windows workstation, Docker for the "remote" server
- Docker mimics remote server — same scripts, same SSH, localhost:2222
- Remote (future): RHEL 9+, JDK pre-installed, SSH access
- Pivot to real servers = change a hostname in config. Nothing else changes.

### Plugin Architecture
Sunwell is a Claude Code skill plugin. The whole repo is the plugin — users
will install it via `claude plugin install <url>` to get all skills namespaced
as `/sunwell:*`. During development, load it locally with `claude --plugin-dir .`.

Skills are temporarily in `.claude/skills/<name>/SKILL.md` for frictionless
local development (auto-discovered, no `--plugin-dir` flag needed). When the
harness is ready to publish, skills migrate to `skills/<name>/SKILL.md` and
the plugin manifest at `.claude-plugin/plugin.json` is updated to point there.
No flat `.claude/commands/` files — skills only.

### Spec Workflow Skill
The `/sunwell:spec` skill (`skills/spec/SKILL.md`) encodes the three-phase
development workflow: requirements → design → tasks. It auto-invokes when
planning language is detected and guides one artifact at a time, stopping
for review between phases.

This skill is project-agnostic — it lives in sunwell temporarily. Extract it
to a personal skill (`~/.claude/skills/spec-workflow/`) when a second project
needs it. At that point it becomes `/spec` everywhere.

See `.claude/skills/spec/SKILL.md` for the full playbook.

### Experiment Tree
The loop tracks every profiling run and code experiment in `results/experiments.json`
(gitignored). Each experiment records: hypothesis, files changed, JFR recording path,
analysis path, and delta vs. baseline. Claude reads and updates this file across
loop iterations to maintain continuity across sessions.

### Distribution (future, not now)
When the harness is stable, publish the plugin to the Claude Code marketplace.
Users install once; skills update via the plugin manager. Not a current concern.

## Conventions

- Java 21
- Maven (not Gradle)
- JMH for benchmarking
- JFR for profiling (initially), async-profiler later
- Results always gitignored (results/ directory)
- Scripts are thin and transport-only — logic lives in skills, not bash
- Scripts are co-located with their skill (e.g., `deploy/deploy-ssh.sh`), not in a top-level `scripts/` directory
- Bad implementations in toy-app should be subtle and realistic

## Git Hygiene

After any task, the IntelliJ/PyCharm commit panel must show zero unversioned files.
Every file is either tracked or gitignored — nothing floats.

Rules for Claude:
- **Creating a file** → `git add <file>` immediately after writing it
- **Moving a file** → use `git mv` instead of `mv` for tracked files
- **Deleting a file** → use `git rm` for tracked files
- **New directory of files** → `git add <dir>/` after all files are written
- **File that should not be tracked** → add the pattern to `.gitignore` first,
  then create the file

The test: run `git status` at the end of any task. If "Untracked files" is
non-empty, either `git add` them or `.gitignore` them before considering the
task done.

## Commit Messages

Follow Linus Torvalds style — no signing required:

- Subject line: imperative present tense, ≤72 chars, no trailing period
- Blank line after subject
- Body: wrap at 72 chars, explain *why* and provide context/color
- No bullet spam in the subject; detail goes in the body

Example:

    Add deploy script and sunwell:deploy skill

    Closes the build→deploy leg of the loop. The script packages the
    toy-app uber JAR via Maven and scps it to the target server, then
    SSHs in to verify the JAR landed and Java is available.

    The deploy skill gives Claude a structured playbook for this stage
    so future sessions can invoke it without re-deriving the steps.

## Vocabulary

- **Harness** — the Sunwell framework itself
- **The Loop** — Build → Deploy → Profile → Collect → Analyze → Tune → Repeat
- **Perf Target** — configuration describing one app's deployment + profiling setup
- **Toy App** — example app with intentionally bad implementations,
  used for development and demonstration of the loop
- **Skill** — `SKILL.md` file in `skills/<name>/` that gives Claude a runnable
  playbook for one stage of the loop; exposed as `/sunwell:<name>`
- **Loop** — the `/sunwell:loop` skill; orchestrates all stages autonomously
- **Experiment Tree** — `results/experiments.json`; tracks every profiling run
  and code experiment with measured deltas vs. baseline