# Repeaters/Foci 0.5.7 and Directional Antenna 0.2.8

This is a maintenance release. Signal power, decay, dimensional rules, recipes, directional reception curves and routing cadence are unchanged.

- Directional integrates through a small optional reception API instead of mixins into Repeaters classes. It still works with Simple Radio alone. When installed together, use core 0.5.7 or newer; Foci uses the matching core version.
- Loading or unloading a native receiver no longer invalidates the relay graph. A change to a relay's connected dishes still invalidates its links immediately; unchanged mast scans retain the cache.
- Minecraft test fixtures now live in dedicated source sets. Normal release JARs contain no addon tests. The prototype filename is a plain copy of that JAR, replacing the custom bytecode filter.
- Named relay status constants and expanded formatting make the block lifecycle, routing and manager code easier to review. Display values and saved numeric status codes are unchanged.
- The wide-array tooltip matches the handbook: 66% through 45°, 44% at 60°, 22% at 75°, zero at 90°, with continuous falloff. The asset generator no longer overwrites language resources.
- Transponder examples distinguish Simplex, Duplex and Multicast forwarding. The README now focuses on installation and first use.

The parabolic tooltip describes its full directional reception boost through 30°. This is not a literal doubling of the antenna score: the existing formula derives an additional reception allowance from the tower's nominal transmission range. It does not increase transmit power. The numerical balance is unchanged.

See `validation/0.5.7/` for this build's verification, and [Development](Development.md) for reproducible commands.
