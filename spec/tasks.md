# Tasks: async-profiler Integration and Preferred-Profiler Routing

## Commit 1 — Docker + Spikes

- [x] 1. Look up the SHA256 checksum for `async-profiler-4.3-linux-x64.tar.gz`
         on the async-profiler GitHub releases page.
         Result: `69a16462c34c06ff55618f41653cffad1f8946822d30842512a3e0e774841c06`

- [x] 2. Update `examples/docker/Dockerfile`:
         - Add `curl` to the existing `apt-get install` line (alongside `openssh-server`)
         - Add `ARG ASYNC_PROFILER_VERSION=4.3` and `ARG ASYNC_PROFILER_SHA256=<hash>`
           near the top of the file
         - Add a `RUN` layer that downloads, verifies SHA256, extracts to
           `/opt/async-profiler/`, and removes the tarball
         - Add `ASYNC_PROFILER_HOME=/opt/async-profiler` to `/etc/environment`

- [x] 3. Rebuild the Docker image and restart the container. Verified
         `/opt/async-profiler/lib/libasyncProfiler.so` present; `asprof --version`
         reports async-profiler 4.3.

- [x] 4. Deploy the toy-app JAR to the container via Maven + SCP.

- [x] 5. Run Spike A — cpu event types:
         Event types present: `jdk.ExecutionSample` (plus housekeeping).
         `summarize-cpu.java` produces populated output — compatible as-is.

- [x] 6. Run Spike A — alloc event types:
         Event types present: `jdk.ObjectAllocationInNewTLAB` (not
         `jdk.ObjectAllocationSample`). `summarize-alloc.java` produces
         empty output — update required in Commit 3.

- [x] 7. Run Spike B — JMH flag syntax confirmed:
         `event=cpu` → `jfr-cpu.jfr`, `event=alloc` → `jfr-alloc.jfr`.
         Files land at `<dir>/<benchmark-fqn>-<mode>/jfr-{event}.jfr`.
         Analyze skill glob must change from `**/profile.jfr` to `**/*.jfr`.

- [x] 8. Spike A and Spike B findings recorded in `spec/design.md`.

- [x] 9. `git add` all changed files; committed and pushed.

---

## Commit 2 — Profile Skill: Detection, Routing, JMH Flags, Override

- [ ] 10. Rename `.claude/skills/profile/profile-jfr.sh` to `profile-run.sh`
          using `git mv`. Add an eighth parameter `<profiler-flag>` to the
          script signature. Replace the hardcoded `-prof jfr:dir=...` on
          line 57 with the passed-in value. Update the usage comment.

- [ ] 11. Update the profile skill (SKILL.md) Step 2 routing table to replace
          the hard-stop block with the detection-and-fallback logic:
          - baseline/gc → JFR always (no probe)
          - cpu/memory/lock → probe target, use async-profiler if found,
            else JFR; apply profiler-override before probe if set
          - Log which profiler was selected and why

- [ ] 12. Add the SSH detection probe as a new Step 2a in the skill:
          single SSH command checking for
          `/opt/async-profiler/lib/libasyncProfiler.so`.

- [ ] 13. Add focus → async-profiler event mapping to the skill:
          cpu → `event=cpu`, memory → `event=alloc`, lock → `event=lock`.

- [ ] 14. Update the script invocation in Step 4 of the skill to pass the
          constructed profiler flag string as the eighth argument.
          Update the script filename reference from `profile-jfr.sh` to
          `profile-run.sh`.

- [ ] 15. Add `profile.profiler-override` parsing to Step 1 of the skill
          (read sunwell.yml). Apply override in the routing logic.

- [ ] 16. Add the commented `profile.profiler-override` example block to
          `examples/toy-app/sunwell.yml`.

- [ ] 17. Verify end-to-end: profile with `--focus cpu` and `--focus memory`
          against the Docker container; confirm recordings are collected and
          experiments.json shows `"profiler": "async-profiler"`.

- [ ] 18. `git add` all changed files; commit.

---

## Commit 3 — Analyze Skill: Profiler Context and Safepoint Awareness (sketch)

- [ ] 19. Update `summarize-alloc.java` to accept `jdk.ObjectAllocationInNewTLAB`
          and `jdk.ObjectAllocationOutsideTLAB` (weight field: `allocationSize`)
          in addition to `jdk.ObjectAllocationSample` (weight field: `weight`).
          `summarize-cpu.java` needs no changes (Spike A confirmed compatible).
- [ ] 20. Change the analyze skill recording discovery glob from `**/profile.jfr`
          to `**/*.jfr` (Spike B finding: async-profiler filenames differ).
- [ ] 21. Update analyze skill to read `profiler` from experiments.json entry.
- [ ] 22. Add `**Profiler:**` line to analysis.md header template.
- [ ] 23. Inject safepoint-bias note into subagent prompt (direction depends on
          profiler: JFR → warn, async-profiler → clear).
- [ ] 24. `git add` all changed files; commit.

---

## Commit 4 — Documentation (sketch)

- [ ] 25. Replace profiler table in `CLAUDE.md` with the preferred/fallback
          table and rationale from requirements.md.
- [ ] 26. Update profile skill `description:` frontmatter and focus routing
          table to reflect detection-and-fallback logic.
- [ ] 27. `git add` all changed files; commit.
