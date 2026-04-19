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
| async-profiler version | 4.3 | Latest stable at time of writing; JFR output format is mature in 4.x |
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
ARG ASYNC_PROFILER_VERSION=4.3
ARG ASYNC_PROFILER_SHA256=69a16462c34c06ff55618f41653cffad1f8946822d30842512a3e0e774841c06
```

Then, in a single subsequent `RUN` layer, download, verify, and extract using
those args:

```dockerfile
RUN curl -fL "https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/async-profiler-${ASYNC_PROFILER_VERSION}-linux-x64.tar.gz" \
      -o /tmp/async-profiler.tar.gz && \
    echo "${ASYNC_PROFILER_SHA256}  /tmp/async-profiler.tar.gz" | sha256sum -c - && \
    tar xz -C /opt -f /tmp/async-profiler.tar.gz && \
    mv "/opt/async-profiler-${ASYNC_PROFILER_VERSION}-linux-x64" /opt/async-profiler && \
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

- **cpu profiling event types:** `jdk.ExecutionSample` (plus standard JVM
  housekeeping events). Same event type name as native JFR profiling.
- **alloc profiling event types:** `jdk.ObjectAllocationInNewTLAB` (plus
  standard housekeeping). Different from native JFR profiling which uses
  `jdk.ObjectAllocationSample`.
- **summarize-cpu.java result:** compatible as-is. Reads `jdk.ExecutionSample`,
  which async-profiler also emits.
- **summarize-alloc.java result:** NOT compatible. Script filters on
  `jdk.ObjectAllocationSample` (line 52) and reads the `weight` field.
  async-profiler emits `jdk.ObjectAllocationInNewTLAB` with an `allocationSize`
  field instead. Script produces empty output against async-profiler recordings.
- **Fix required in Commit 3:** update `summarize-alloc.java` to accept both
  `jdk.ObjectAllocationSample` (weight: `weight` field) and
  `jdk.ObjectAllocationInNewTLAB` (weight: `allocationSize` field).
  `jdk.ObjectAllocationOutsideTLAB` should also be handled for completeness
  (same `allocationSize` field).

#### Spike B — JMH Flag Syntax

- **Confirmed flag string (cpu):**
  `-prof "async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=cpu;output=jfr;dir=/tmp/<run-id>"`
- **Confirmed flag string (alloc):**
  `-prof "async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=alloc;output=jfr;dir=/tmp/<run-id>"`
- **Recording path structure:** `<dir>/<benchmark-fqn>-<mode>/jfr-{event}.jfr`
  e.g. `.../CpuHogBenchmark.deduplicateTags-Throughput/jfr-cpu.jfr` and
  `.../MemoryHogBenchmark.buildReport-Throughput/jfr-alloc.jfr`
- **Matches collect-ssh.sh expectation:** Partially. The directory structure
  is the same, but the filename is `jfr-{event}.jfr` rather than `profile.jfr`.
  collect-ssh.sh copies the whole directory tree (no filename filter), so
  collection works fine. The analyze skill's recording discovery glob must
  change from `**/profile.jfr` to `**/*.jfr` to find async-profiler recordings.
- **Notes:** The `-prof jfr` and `-prof async` flags are mutually exclusive
  per benchmark run — the profile skill selects one and constructs the flag
  accordingly. No conflict.

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

## Commit 2 — Profile Skill: Detection, Routing, JMH Flags, Override (detailed)

### Scope

Acceptance criteria addressed: Profiler Detection (all four), async-profiler
JMH Integration (first two), Override (all three). Also removes the hard-stop.

### Approach

The profile skill constructs the full JMH profiler flag string and passes it
to the profile script as a parameter. The script remains thin and
transport-only — it does not select a profiler. All routing logic stays in
the skill.

`profile-jfr.sh` is renamed `profile-run.sh` and gains an eighth parameter:
the profiler flag string. The hardcoded `-prof jfr:dir=...` on line 57 is
replaced with the passed-in value.

Detection is a single inline SSH command in the skill — not a script. It
checks for the `libasyncProfiler.so` at the known install path and reports
found/not-found. One SSH round-trip, result used immediately for routing.

### Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Profiler flag ownership | Skill constructs, script receives as param | Per CLAUDE.md: logic in skills, scripts are transport-only |
| Script rename | `profile-jfr.sh` → `profile-run.sh` | The script now handles both JFR and async-profiler runs; the old name would be misleading |
| Detection location | Inline SSH in skill (not a script) | A one-liner check doesn't warrant a script; keeps the transport boundary clean |
| Detection target | `/opt/async-profiler/lib/libasyncProfiler.so` | Known install path from Commit 1; `asprof` binary is a secondary confirmation but the `.so` is what JMH needs |
| Override schema | `profile.profiler-override` map in sunwell.yml | Consistent with the existing `analyze.hints` block style; per-focus granularity |

### sunwell.yml Override Schema

```yaml
# Optional — force a specific profiler for one or more focuses.
# Valid profiler values: jfr, async-profiler
# profile:
#   profiler-override:
#     cpu: jfr        # force JFR even if async-profiler is available
#     memory: jfr
```

### Routing Logic (in skill, Step 2)

```
if focus in {baseline, gc}:
    profiler = jfr                        # always; no probe needed
elif profile.profiler-override[focus] is set:
    profiler = override value             # explicit operator choice
elif /opt/async-profiler/lib/libasyncProfiler.so exists on target:
    profiler = async-profiler
else:
    profiler = jfr                        # fallback; log the reason

focus → async-profiler event mapping:
    cpu    → event=cpu
    memory → event=alloc
    lock   → event=lock  (collected; not yet analyzed)
```

### JMH Flag Strings (constructed by skill, passed to script)

```
JFR:            -prof "jfr:dir=<remote-dir>"
async-profiler: -prof "async:libPath=/opt/async-profiler/lib/libasyncProfiler.so;event=<event>;output=jfr;dir=<remote-dir>"
```

### File and Component Changes

| File | Change |
|---|---|
| `.claude/skills/profile/profile-jfr.sh` | Rename to `profile-run.sh`; replace hardcoded `-prof jfr:...` with `$8` (profiler flag param); update usage comment |
| `.claude/skills/profile/SKILL.md` | Add detection step (Step 2a); update routing table; update script invocation to pass profiler flag; remove hard-stop block |
| `examples/toy-app/sunwell.yml` | Add commented `profile.profiler-override` example block |

### Edge Cases and Failure Modes

- If `profiler-override` specifies `async-profiler` but it is not installed,
  the skill reports the conflict and stops rather than silently falling back.
  Operator explicitly asked for it — silent fallback would be surprising.
- If the detection SSH command fails (host unreachable, auth failure), the
  profile step would already fail at the later SSH calls; no special handling.
- `lock` focus: async-profiler is used if available (recording is collected),
  but the analyze skill will find no active dimensions for it until Commit 3
  adds lock analysis. The skill logs a note: "lock analysis not yet available;
  recording collected for future use."

### Deferred to Later Commits

- `**/profile.jfr` → `**/*.jfr` glob fix in analyze skill (Commit 3)
- Profiler context in analysis.md and safepoint-bias prompts (Commit 3)
- `summarize-alloc.java` event type fix (Commit 3)
- CLAUDE.md and skill description updates (Commit 4)

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
