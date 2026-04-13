# Sunwell

> *"Your code thinks it's ready. Sunwell disagrees."*

Sunwell is a Maven-based performance engineering harness for Java applications.
It closes the loop between code changes and empirical performance data, with
Claude as an active participant in every stage.

## The Loop

Build → Deploy → Instrument → Load → Collect → Analyze → Tune → Repeat

Claude orchestrates this loop via slash commands. The loop is invariant.
What varies per application is configuration — which flags, which JAR, which
host. Not Java interfaces.

## Core Principles

> Don't define a Java interface until you have two concrete implementations
> that would otherwise duplicate code.

In early phases, the "framework" is almost entirely Claude commands and
scripts. Java code is the toy app and its JMH benchmarks. Nothing more.

Interfaces will emerge when real shared logic appears across multiple
concrete implementations. Not before.

## Repo Structure

    sunwell/
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
        docker/
          Dockerfile
          docker-compose.yml
      .claude/
        commands/                     <- Claude Code slash commands
          perf-deploy.md
          perf-profile.md
          perf-analyze.md
      pom.xml                         <- parent POM
      CLAUDE.md                       <- this file (permanent)
      WORKING.md                      <- current session plan (temporary, will be deleted)
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

### Distribution (future, not now)
Eventually a sunwell-maven-plugin will install Claude commands and scripts
into a target project. Not built yet. Not a current concern.

## Conventions

- Java 21
- Maven (not Gradle)
- JMH for benchmarking
- JFR for profiling (initially), async-profiler later
- Results always gitignored (results/ directory)
- Scripts are thin — logic lives in Claude commands, not bash
- Bad implementations in toy-app should be subtle and realistic

## Commit Messages

Follow Linus Torvalds style — no signing required:

- Subject line: imperative present tense, ≤72 chars, no trailing period
- Blank line after subject
- Body: wrap at 72 chars, explain *why* and provide context/color
- No bullet spam in the subject; detail goes in the body

Example:

    Add deploy script and perf-deploy Claude command

    Closes the build→deploy leg of the loop. The script packages the
    toy-app uber JAR via Maven and scps it to the target server, then
    SSHs in to verify the JAR landed and Java is available.

    The perf-deploy command gives Claude a structured playbook for this
    stage so future sessions can invoke it without re-deriving the steps.

## Vocabulary

- **Harness** — the Sunwell framework itself
- **The Loop** — Build → Deploy → Profile → Collect → Analyze → Tune → Repeat
- **Perf Target** — configuration describing one app's deployment + profiling setup
- **Toy App** — example app with intentionally bad implementations,
  used for development and demonstration of the loop
- **Claude Command** — markdown file in .claude/commands/ that gives Claude
  a runnable playbook for one stage of the loop