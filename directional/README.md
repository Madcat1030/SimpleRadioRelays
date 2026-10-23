# Simple Radio Directional Antenna 0.2.8

Adds two aimable receiver arrays for Simple Radio towers. **Repeaters and Dimensional Foci are optional.** With Repeaters installed, the same arrays also improve relay reception.

Use Fabric Minecraft 1.20.1, Java 17+, Fabric Loader 0.19.0+, Fabric API and Simple Radio **1.20.1-4.5.7.9** with its dependencies. Install on server and clients. Optional Repeaters integration requires core 0.5.7+.

## Mount and aim

Attach a receiver to a vertical antenna mast with another antenna above it. Leave a clear 3×3 panel area beside the mast. Empty-hand right-click turns it in 15° steps; sneak-right-click turns it back. It swivels up to 45° either way from its mounting direction. Opposite receivers share the mounting rings.

| Receiver | Full central area | Falloff anchors | No extra reception |
| --- | --- | --- | --- |
| Parabolic | Full directional boost through 30° | 50% at 45° | 60° and beyond |
| Wide-angle | 66% of full boost through 45° | 44% at 60°, 22% at 75° | 90° and beyond |

Angles are measured to either side of the aimed direction. The bonus fades continuously between anchors. Overlapping receivers use the strongest bonus; they do not stack. Outside the beam, normal reception remains unchanged.

These devices improve **reception**, not transmit power. Full boost is the maximum directional reception allowance, not a literal doubling of the antenna score. The wider array trades strength for coverage.

The in-game Repeaters handbook gains a Directional Receivers chapter when both mods are installed. The arrays themselves work without the book or Repeaters.

Editable Blockbench projects, native mast references and retained dish alternatives are in [the model kit](art/model-kit/README.md). See [development instructions](../docs/Development.md) for standalone and combined tests.
