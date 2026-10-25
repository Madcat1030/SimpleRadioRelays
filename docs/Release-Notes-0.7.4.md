# Repeaters 0.7.4

Confirmed antenna block edits now update relay hardware separately from chunk-restoration checks. Breaking a mast, its physical core or a known insulator invalidates the verified relay record immediately, including when its relay box is unloaded. The invalidation is saved before deferred scanning, so leaving immediately cannot retain the old active installation.

Geometry rescans are coalesced per affected loaded relay and run after the next native block tick settles. A complete result can publish zero strength without the second ten-second confirmation used for uncertain load scans. Rebuilding or trimming a loaded installation therefore updates within a few ticks. An unloaded relay box retains its invalid state until it can verify the rebuilt hardware. Unrelated same-block state changes, including transmitter activity flags, do not use this path.

The block-change hook observes successful replacements in loaded chunks; ordinary chunk/entity unloading does not invoke it. Missing historical dependencies retain their existing grace for unconfirmed/non-destructive changes, and guarded scans still cannot load remote chunks. A chunk index narrows each mutation to registered antenna footprints. No world scanning was added to audio routing.

Periodic antenna verification remains as a fallback. Mast-tip terrain publication, height sampling, propagation math, and the terrain curve are unchanged.

Use the existing Dimensional Foci 0.7.3 and Directional Antenna 0.2.9 jars. Foci API 1 is unchanged; this core-only update does not require a new Foci release.

## Validation

Passed 101 unit tests, 87 core server GameTests, and 133 combined server GameTests with Foci and Directional installed. Coverage includes immediate persisted invalidation, rebuilding and trimming before the periodic boundary, unloaded relay boxes, ordinary chunk unloading, and unavailable historical dependencies. Production archives contain no test classes; the existing Foci and Directional jars remain byte-identical.
