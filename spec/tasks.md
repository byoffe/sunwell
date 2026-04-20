# Tasks: async-profiler Integration and Preferred-Profiler Routing

## Commit 1 — Docker + Spikes

- ✅ 1. Look up the SHA256 checksum for `async-profiler-4.3-linux-x64.tar.gz`
         on the async-profiler GitHub releases page.
         Result: `69a16462c34c06ff55618f41653cffad1f8946822d30842512a3e0e774841c06`

- ✅ 2. Update `examples/docker/Dockerfile`:
         - Add `curl` to the existing `apt-get install` line (alongside `openssh-server`)
         - Add `ARG ASYNC_PROFILER_VERSION=4.3` and `ARG ASYNC_PROFILER_SHA256=<hash>`
           near the top of the file
         - Add a `RUN` layer that downloads, verifies SHA256, extracts to
           `/opt/async-profiler/`, and removes the tarball
         - Add `ASYNC_PROFILER_HOME=/opt/async-profiler` to `/etc/environment`

- ✅ 3. Rebuild the Docker image and restart the container. Verified
         `/opt/async-profiler/lib/libasyncProfiler.so` present; `asprof --version`
         reports async-profiler 4.3.

- ✅ 4. Deploy the toy-app JAR to the container via Maven + SCP.

- ✅ 5. Run Spike A — cpu event types:
         Event types present: `jdk.ExecutionSample` (plus housekeeping).
         `summarize-cpu.java` produces populated output — compatible as-is.

- ✅ 6. Run Spike A — alloc event types:
         Event types present: `jdk.ObjectAllocationInNewTLAB` (not
         `jdk.ObjectAllocationSample`). `summarize-alloc.java` produces
         empty output — update required in Commit 3.

- ✅ 7. Run Spike B — JMH flag syntax confirmed:
         `event=cpu` → `jfr-cpu.jfr`, `event=alloc` → `jfr-alloc.jfr`.
         Files land at `<dir>/<benchmark-fqn>-<mode>/jfr-{event}.jfr`.
         Analyze skill glob must change from `**/profile.jfr` to `**/*.jfr`.

- ✅ 8. Spike A and Spike B findings recorded in `spec/design.md`.

- ✅ 9. `git add` all changed files; committed and pushed.

---

## Commit 2 — Profile Skill: Detection, Routing, JMH Flags, Override

- ✅ 1. Rename `.claude/skills/profile/profile-jfr.sh` to `profile-run.sh`
         using `git mv`. Added eighth parameter `<profiler-flag>`. Replaced
         hardcoded `-prof jfr:dir=...` with passed-in value. Updated comment.

- ✅ 2. Updated profile skill (SKILL.md) Step 2 routing table: replaced
         hard-stop block with detection-and-fallback logic including override
         check, JFR-always cases, and SSH probe for cpu/memory/lock.

- ✅ 3. Added SSH detection probe as Step 2a: single SSH `test -f` command
         checking for `/opt/async-profiler/lib/libasyncProfiler.so`.

- ✅ 4. Added focus → async-profiler event mapping:
         cpu → `event=cpu`, memory → `event=alloc`, lock → `event=lock`.

- ✅ 5. Updated script invocation in Step 4 to pass profiler flag as eighth
         argument and reference `profile-run.sh`.

- ✅ 6. Added `profile.profiler-override` parsing to Step 1 and routing
         logic including conflict-stop if override requests unavailable profiler.

- ✅ 7. Added commented `profile.profiler-override` example block to
         `examples/toy-app/sunwell.yml`.

- ✅ 8. Verified end-to-end: cpu and memory focuses produced jfr-cpu.jfr
         and jfr-alloc.jfr at expected paths on the Docker target.

- ✅ 9. `git add` all changed files; committed and pushed.

---

## Commit 3 — Analyze Skill: Profiler Context and Safepoint Awareness

- ✅ 1. Update `summarize-alloc.java` to accept `jdk.ObjectAllocationInNewTLAB`
         and `jdk.ObjectAllocationOutsideTLAB` (weight field: `allocationSize`)
         in addition to `jdk.ObjectAllocationSample` (weight field: `weight`).
         `summarize-cpu.java` needs no changes (Spike A confirmed compatible).
- ✅ 2. Change the analyze skill recording discovery glob from `**/profile.jfr`
         to `**/*.jfr` (Spike B finding: async-profiler filenames differ).
- ✅ 3. Update analyze skill to read `profiler` from experiments.json entry.
- ✅ 4. Add `**Profiler:**` line to analysis.md header template.
- ✅ 5. Inject safepoint-bias note into subagent prompt (direction depends on
         profiler: JFR → warn, async-profiler → clear).
- ✅ 6. `git add` all changed files; commit.

---

## Commit 4 — Documentation (sketch)

- [ ] 1. Replace profiler table in `CLAUDE.md` with the preferred/fallback
         table and rationale from requirements.md.
- [ ] 2. Update profile skill `description:` frontmatter and focus routing
         table to reflect detection-and-fallback logic.
- [ ] 3. `git add` all changed files; commit.
