# 0.7.0 validation

- 94 JUnit tests passed: 75 core, 2 Foci, 17 Directional.
- 81 core-only Minecraft server tests passed.
- 127 combined Minecraft server tests passed with Repeaters, Dimensional Foci and Directional Antenna.

New monitor tests exercise real placed blocks and front-face clicks in all four facings, no-card behavior, separate transponder input/output roles, one count per physical node, AM/FM filtering, saved unloaded targets, passive forwarding snapshots, cache reuse, page order, exact-coordinate omission from TX-only client updates, stacked and offhand compass binding, native lodestone tracking and ItemStack saves, monitor reload, harvest tags/loot, synced recipe availability, and shared terrain percentage reporting without target chunk loads. Unit tests cover all sixteen bearings, sector boundaries, north wraparound and distance rounding. The explicit development test filter includes the new MonitorGameTests class.

Native screenshots use the actual Minecraft 1.20.1 baked models, card part and block entity renderer on a QA preview screen. The displayed NBT pages come from the physical-monitor server test, rather than invented text in a raster render. A separate geometry check verifies integer voxel coordinates, one texel per model unit, and zero overlapping coplanar faces for the combined housing/card model. QA and host setup classes are excluded from production artifacts.

Foci changes only fabric.mod.json; its seven classfiles are byte-identical to 0.6.1. Directional 0.2.9 remains byte-identical. Standard and prototype artifacts are identical, all production classes target Java 17 and no custom obfuscation is used. Development execution uses Java 21. The malformed-config regression intentionally logs a rejected parse; client bootstrap can log unavailable authentication/audio services on this disposable headless host. Neither is a gameplay dependency introduced by this release.

These checks validate the diagnostic behavior and rendering; no live microphone playback session was performed for this release. Radio propagation mathematics are reused without changes.
