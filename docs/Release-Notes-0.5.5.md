# Simple Radio Repeaters 0.5.5

Adds an operator-only performance recording for measuring the current router before choosing an optimization. Routing decisions, RF math, packet rate, duplicate handling, both entrance checks, candidate loops and cache policy are unchanged. The capture does not generate test audio or construct an alternate delivery plan.

```text
/radiorepeater test start
/radiorepeater test stop
/radiorepeater test start 60
```

`start` records until stopped; `start <seconds>` automatically saves after 1–3600 elapsed seconds. Duration uses the monotonic clock and stops on the first server tick after the deadline, so lag does not shorten the recording. A second start is rejected while a capture is active. Shutdown also saves an active capture. Permission level 2 is required. The existing plural `/radiorepeaters` commands remain available; `/radiorepeater` is an alias.

Each completed capture writes a uniquely named JSON file under `logs/radiorepeaters/`. The command reports the filename; timed completion reports it to the command initiator. `logs/latest.log` gets a `[relay-test]` start/stop summary. Voice contents are never recorded. There is no per-packet file logging.

The report includes:

- Capture duration, observed ticks/TPS, installed radio-mod versions, server RF configuration, catalyst strengths and starting/ending tower descriptions.
- Route calls and forwarded frames; initial/final entrance passes and RF checks; direct and relayed receiver comparisons; accepted candidates; receiver skips; custom criteria, callback and wire activity; delivery attempts and actual accepted deliveries.
- Elapsed timings for network lookup, both entrance passes, callbacks/wires, path search/merge, output handling, receiver filtering, direct candidates, relay candidates, winner selection and native delivery.
- Path/link-row/network cache hits and misses, evictions, oversized entries that were not retained, network rebuild times and invalidation reasons. Explicit `/diagnose` work is separated from live routing and maintenance.
- Unchanged tower snapshots and load/unload notifications, making unnecessary graph rebuilds distinguishable from normal antenna checks. Snapshot changes identify affected fields such as antenna origin, strength, dependency footprint, catalyst or channels.
- Actual antenna scan timings and completion outcomes, per-input-transmitter summaries, repeated/changed RF input state, callback RF changes, receiver state/replacement observations and movement over eight blocks.
- A one-second activity timeline and cache/invalidation events. Long captures retain the latest 600 timeline buckets and 256 events; aggregate totals continue. At most 128 transmitter flows and 512 receiver states are tracked, with explicit overflow counts.

Mean and maximum timings use measured nanoseconds. Percentiles are labeled `p50_upper_us`, `p95_upper_us` and `p99_upper_us`: they are upper bounds from a fixed-memory powers-of-two histogram. `route_self` and phase self timings exclude time spent in nested repeater calls during wired re-entry; `route_inclusive` includes that child work and must not be summed as independent CPU usage. Graph-build timings can overlap routing's network-lookup phase. Captured timings include instrumentation and JVM/OS pauses; this is not a CPU-sampling or allocation profiler. Source-copy counts cover copies explicitly made by our router, not additional native copies.

The recorder is disabled by default. When disabled, observer hooks perform lightweight inactive checks without creating per-packet samples, reading profiling clocks or writing reports. Start/stop does not clear or warm the routing caches. Directional Antenna's existing generic extension invalidations appear as `extension_or_external_reload`; server datapack reload and the Foci config command have separate reason labels.

For comparison, let the current network settle to READY, stand in one location and record 60 seconds while speaking. Add the extra towers, wait for READY, then repeat from the same location with the same receivers and approximately the same speaking time. Keep both JSON files. A separate movement recording can then show how often movement/state changes would invalidate a future delivery-plan cache. Avoid `/diagnose` during the steady-traffic comparison; if used, its graph/cache work remains labeled separately.

Core and matching Dimensional Foci packages are 0.5.5. Directional Antenna remains 0.2.6. Prototype jars exclude automated test fixtures and retain normal readable class names. Source, test mechanics and validation evidence accompany the release.
