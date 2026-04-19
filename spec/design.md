# Design: async-profiler Integration and Preferred-Profiler Routing

---

## Commit 1 — Docker + Spikes (detailed)

### Scope

Addresses requirements: Spike A (event format), Spike B (JMH flag syntax).
Acceptance criteria satisfied: Spike A finding documented, Spike B finding
documented.

No skill logic is changed in this commit. The deliverable is two documented
findings that gate all subsequent commits.

### Approach

Install async-profiler into the Docker image at build time. Rebuild the
container, deploy the toy-app JAR, then run both spikes via SSH. Record
findings in this document before committing.

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| async-profiler version | 3.0 | Latest stable at time of writing; JFR output format is mature in 3.x |
| Install path | `/opt/async-profiler/` | Consistent with conventions for optional tooling; matches the probe path in the planned detection logic |
| Install method | `curl` + tar in Dockerfile `RUN` layer | No package manager entry for async-profiler; GitHub releases tarball is the canonical distribution |
| Spike execution | Manual SSH commands, not via `/profile` skill | The spike must test the flag syntax before the skill encodes it; using the skill would assume the answer |

### File and Component Changes

| File | Change |
|---|---|
| `examples/docker/Dockerfile` | Add `curl` install and async-profiler download/extract to `/opt/async-profiler/` |
| `spec/design.md` | Append Spike A and Spike B findings sections after running |

### Dockerfile Change (detail)

Add `curl` to the existing `apt-get install` line — do not introduce a
separate `RUN` layer. Each additional `apt-get update` layer bloats the image
and duplicates package index fetches:

```dockerfile
RUN apt-get update && apt-get install -y openssh-server curl && rm -rf /var/lib/apt/lists/*
```

Declare version and checksum as `ARG` at the top of the Dockerfile — build-time
only, not persisted into the image. Both travel as a pair; upgrading async-profiler
means changing exactly these two lines:

```dockerfile
ARG ASYNC_PROFILER_VERSION=3.0
ARG ASYNC_PROFILER_SHA256=<sha256-from-releases-page>
```

Then, in a single subsequent `RUN` layer, download, verify, and extract using
those args:

```dockerfile
RUN curl -fL "https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/async-profiler-${ASYNC_PROFILER_VERSION}-linux-x64.tar.gz" \
      -o /tmp/async-profiler.tar.gz && \
    echo "${ASYNC_PROFILER_SHA256}  /tmp/async-profiler.tar.gz" | sha256sum -c - && \
    tar xz -C /opt -f /tmp/async-profiler.tar.gz && \
    mv /opt/async-profiler-${ASYNC_PROFILER_VERSION}-linux-x64 /opt/async-profiler && \
    rm /tmp/async-profiler.tar.gz
```

`ASYNC_PROFILER_SHA256` must be filled in from the async-profiler GitHub releases
page for the chosen version before committing. The `sha256sum -c -` check causes
the build to fail loudly if the download is corrupt or substituted — do not skip it.

GitHub releases is the canonical and only official distribution channel for
async-profiler; there is no apt/yum package. The pinned SHA256 is the
appropriate integrity control given that the project does not GPG-sign releases.

`ARG` (not `ENV`) is correct here — version and hash are build-time concerns.
`ENV` is reserved for values that running processes need (e.g., `ASYNC_PROFILER_HOME`).

Make the lib path accessible in non-interactive SSH sessions (same pattern as
`JAVA_HOME`):

```dockerfile
RUN echo 'ASYNC_PROFILER_HOME=/opt/async-profiler' >> /etc/environment
```

### Spike Execution Steps

**Prerequisites:** container rebuilt and running, toy-app JAR deployed via
`/deploy examples/toy-app`.

**Spike A — Event Type Inspection**

Run a minimal cpu-profiled benchmark (1 fork, 1 iteration, 5 s):

```
ssh -p 2222 -i examples/docker/sunwell_dev_key sunwell@localhost \
  "java -jar /home/sunwell/toy-app-benchmarks.jar \
    'CpuHogBenchmark' \
    -prof 'async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=cpu;output=jfr;dir=/tmp/spike-cpu' \
    -wi 0 -i 1 -f 1 -r 5s"
```

Then inspect the recording:

```
ssh -p 2222 -i examples/docker/sunwell_dev_key sunwell@localhost \
  "find /tmp/spike-cpu -name '*.jfr' | head -1 | xargs -I{} \
   java -XX:+UnlockDiagnosticVMOptions -XshowSettings:all \
   -cp /opt/java/openjdk/lib/jfr.jar jdk.jfr.tool.Main print --json {} 2>/dev/null \
   | head -100"
```

Simpler alternative — list event type names only using a two-line Java
snippet piped through `java --source 21`:

```
ssh -p 2222 -i examples/docker/sunwell_dev_key sunwell@localhost "
java --source 21 - <<'EOF'
import jdk.jfr.consumer.*;
import java.nio.file.*;
var f = RecordingFile.openFiles(java.util.List.of(Path.of(\"$(find /tmp/spike-cpu -name '*.jfr' | head -1)\")));
f.readAllEvents().stream().map(e -> e.getEventType().getName()).distinct().sorted().forEach(System.out::println);
f.close();
EOF"
```

Repeat with `event=alloc` and a `MemoryHogBenchmark` run.

Record all distinct event type names found for cpu and alloc runs.
Run the existing summarize scripts against these recordings to confirm
whether they produce populated output or empty tables.

**Spike B — JMH Flag Syntax**

Confirm the exact `-prof async:...` string accepted by JMH 1.37:

```
ssh -p 2222 -i examples/docker/sunwell_dev_key sunwell@localhost \
  "java -jar /home/sunwell/toy-app-benchmarks.jar \
    'CpuHogBenchmark' \
    -prof 'async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=cpu;output=jfr;dir=/tmp/spike-b' \
    -wi 1 -i 2 -f 1 -r 3s 2>&1 | tail -30"
```

Observe: does it exit cleanly? Where do `.jfr` files land? Are they at
`/tmp/spike-b/<benchmark>-<mode>/profile.jfr` (matching collect-ssh.sh
expectations) or a different structure?

Also test `event=alloc` to confirm the alloc path.

### Findings (filled in during implementation)

#### Spike A — Event Types

*(To be recorded after running the spike)*

- **cpu profiling event types:**
- **alloc profiling event types:**
- **summarize-cpu.java result:** compatible / needs update
- **summarize-alloc.java result:** compatible / needs update
- **Notes:**

#### Spike B — JMH Flag Syntax

*(To be recorded after running the spike)*

- **Confirmed flag string (cpu):**
- **Confirmed flag string (alloc):**
- **Recording path structure:**
- **Matches collect-ssh.sh expectation:** yes / no / adjustment needed
- **Notes:**

### Edge Cases and Failure Modes

- If the async-profiler tarball URL has changed for the chosen version, the
  `curl -f` flag causes an explicit non-zero exit. Fix: verify the URL on the
  releases page before committing the Dockerfile.
- If the pinned SHA256 does not match (corrupted download, CDN substitution),
  `sha256sum -c -` fails the build loudly. This is the intended behavior.
- `eclipse-temurin:21-jdk` does not include `curl` by default; adding it to
  the existing `apt-get install` line keeps it in a single layer.
- If the inline Java event-type snippet fails (classpath or `--source` issues),
  fall back to `jfr print --summary <file>` which lists event counts by type.

### Deferred to Later Commits

All profiler routing logic, skill changes, override support, and documentation
updates. Everything depends on the spike findings.

---

## Commit 2 — Profile Skill: Detection, Routing, JMH Flags, Override (sketch)

**Scope:** Profile skill end-to-end for cpu and memory focuses using
async-profiler; fallback to JFR when not available; override mechanism;
remove hard-stop for cpu/memory/lock.

**Approach (subject to Spike B findings):**
- Add an SSH detection step: probe `/opt/async-profiler/lib/libasyncProfiler.so`
  (and `which asprof` as a secondary check). Cache the result for the run.
- Build the JMH profiler flag string based on detected profiler + focus:
  - async-profiler available + cpu: `event=cpu;output=jfr`
  - async-profiler available + memory: `event=alloc;output=jfr`
  - async-profiler available + lock: fall back to JFR (no lock analysis yet)
  - async-profiler not available: JFR for all focuses
- Read `profile.profiler-override` from sunwell.yml; if set for this focus,
  use the specified profiler regardless of detection result.
- Remove the hard-stop block in the profile skill. lock gets JFR silently.
- Update experiments.json `profiler` field to reflect what was actually used.

**Files touched:** `.claude/skills/profile/SKILL.md`,
`examples/toy-app/sunwell.yml` (commented override example).

**Gates on:** Spike B confirmed flag string and path structure.

---

## Commit 3 — Analyze Skill: Profiler Context and Safepoint Awareness (sketch)

**Scope:** Analyze skill reads which profiler produced the recording and uses
that context in analysis.md and subagent prompts. Summarize script updates
if Spike A found mismatches.

**Approach (subject to Spike A findings):**
- Read `profiler` from the experiments.json run entry.
- Add `**Profiler:** <jfr|async-profiler>` to the analysis.md header block.
- Inject profiler context into the subagent interpretation prompt:
  - JFR: *"Note: ExecutionSample events are safepoint-biased — hotspots in
    tight loops may be under-represented."*
  - async-profiler: *"Note: this data was collected by async-profiler using
    AsyncGetCallTrace — not safepoint-biased; hotspots reflect true CPU time."*
- If Spike A found event type mismatches: update the affected summarize
  script(s) to query the async-profiler event type names in addition to (or
  instead of) the JDK event type names.

**Files touched:** `.claude/skills/analyze/SKILL.md`, and conditionally
`summarize-cpu.java` and/or `summarize-alloc.java`.

**Gates on:** Spike A event type findings.

---

## Commit 4 — Documentation (sketch)

**Scope:** CLAUDE.md profiler table, profile skill description, finalize any
inline comments added during implementation.

**Approach:**
- Replace the current simple profiler table in CLAUDE.md with the
  preferred/fallback table from requirements.md, including rationale column.
- Update the profile skill `description:` frontmatter to reflect
  detection-and-fallback rather than JFR-only.
- Update the focus table in the profile skill body to show preferred profiler
  and fallback for each focus rather than the current hard-stop notation.

**Files touched:** `CLAUDE.md`, `.claude/skills/profile/SKILL.md` (description
and focus table).

**No gates** — documentation-only; can proceed once Commits 2 and 3 are done.
