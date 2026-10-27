# Repeaters 0.7.6

Relay Monitor compass bindings from TX-only pages now mark the displayed bearing and rounded distance rather than the exact antenna location. Repeated bindings with the same visible estimate produce the same point. Native dimension scaling and Foci projection coordinates are converted back to blocks in the physical destination dimension; the remote tower's height is not copied into an estimated target.

TX/RX and RX-only pages retain exact antenna binding. Existing compasses remain fixed when reception changes. Rebinding once a tower is heard replaces the estimate with its exact position. Existing bound compasses are not migrated or rewritten. Stack handling, offhand use and unrelated item data are preserved. The in-game guide describes the distinction and the binding message identifies an estimated target.

This release implements the requested compass behavior only. Survey/terrain status coupling, the Mapped label, strict terrain-curve validation and the zero-tower block-hook fast path remain unchanged for review.

Foci API 1 is unchanged. Keep Dimensional Foci 0.7.3 and Directional Antenna 0.2.9.

## Validation

Passed 109 unit tests, 93 core server tests and 139 combined server tests. Tests cover static estimates, exact rebinding after RX, stack/offhand behavior, no remote chunk loading, Nether scaling and both Foci projection directions. Foci and Directional production jars remain byte-identical.
