# Tasks: async-profiler Integration and Preferred-Profiler Routing

## Commit 1 — Docker + Spikes

- [ ] 1. Look up the SHA256 checksum for `async-profiler-3.0-linux-x64.tar.gz`
         on the async-profiler GitHub releases page.

- [ ] 2. Update `examples/docker/Dockerfile`:
         - Add `curl` to the existing `apt-get install` line (alongside `openssh-server`)
         - Add `ARG ASYNC_PROFILER_VERSION=3.0` and `ARG ASYNC_PROFILER_SHA256=<hash>`
           near the top of the file
         - Add a `RUN` layer that downloads, verifies SHA256, extracts to
           `/opt/async-profiler/`, and removes the tarball
         - Add `ASYNC_PROFILER_HOME=/opt/async-profiler` to `/etc/environment`

- [ ] 3. Rebuild the Docker image and restart the container. Verify
         `/opt/async-profiler/lib/libasyncProfiler.so` exists in the container.

- [ ] 4. Deploy the toy-app JAR (`/deploy examples/toy-app`) so the benchmarks
         are available for the spikes.

- [ ] 5. Run Spike A — cpu event types:
         - Run `CpuHogBenchmark` with `event=cpu;output=jfr` via SSH
         - List all JFR event type names in the recording
         - Run `summarize-cpu.java` against the recording; note whether output
           is populated or empty

- [ ] 6. Run Spike A — alloc event types:
         - Run `MemoryHogBenchmark` with `event=alloc;output=jfr` via SSH
         - List all JFR event type names in the recording
         - Run `summarize-alloc.java` against the recording; note whether output
           is populated or empty

- [ ] 7. Run Spike B — confirm JMH flag syntax and recording path structure:
         - Run a benchmark with the full `-prof async:...` flag string
         - Confirm the exact flag string that produces clean output
         - Confirm where `.jfr` files land relative to the `dir=` argument
         - Note whether the path structure matches what `collect-ssh.sh` expects

- [ ] 8. Record all findings in the Spike A and Spike B sections of
         `spec/design.md`.

- [ ] 9. `git add` all changed files; commit.

---

## Commit 2 — Profile Skill: Detection, Routing, JMH Flags, Override (sketch)

- [ ] 10. Add SSH detection probe for async-profiler to the profile skill.
- [ ] 11. Implement focus → profiler routing with fallback (cpu/memory → async-profiler
          if available, else JFR; lock → JFR always for now; baseline/gc → JFR always).
- [ ] 12. Build correct JMH flag string for async-profiler (event=cpu or event=alloc)
          using confirmed Spike B syntax.
- [ ] 13. Add `profile.profiler-override` support to sunwell.yml schema and
          profile skill; add commented example to toy-app sunwell.yml.
- [ ] 14. Remove the hard-stop for cpu/memory/lock focuses.
- [ ] 15. Ensure `profiler` field in experiments.json is always populated.
- [ ] 16. `git add` all changed files; commit.

---

## Commit 3 — Analyze Skill: Profiler Context and Safepoint Awareness (sketch)

- [ ] 17. Update analyze skill to read `profiler` from experiments.json entry.
- [ ] 18. Add `**Profiler:**` line to analysis.md header template.
- [ ] 19. Inject safepoint-bias note into subagent prompt (direction depends on
          profiler: JFR → warn, async-profiler → clear).
- [ ] 20. Update summarize scripts if Spike A found event type mismatches
          (may be a no-op if compatible as-is).
- [ ] 21. `git add` all changed files; commit.

---

## Commit 4 — Documentation (sketch)

- [ ] 22. Replace profiler table in `CLAUDE.md` with the preferred/fallback
          table and rationale from requirements.md.
- [ ] 23. Update profile skill `description:` frontmatter and focus routing
          table to reflect detection-and-fallback logic.
- [ ] 24. `git add` all changed files; commit.
