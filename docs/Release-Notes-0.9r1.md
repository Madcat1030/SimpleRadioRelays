# Simple Radio Addons 0.9r1

Release candidate for Fabric Minecraft 1.20.1 / Simple Radio 1.20.1-4.5.7.9. All three addon builds identify themselves internally as `0.9.0-rc.1`.

- Passive Relay Monitor surveys can still request shared terrain samples, but no longer mark nearby forwarding relays SCAN/CHECK.
- Invalid terrain curves no longer discard valid feature switches and other settings. The last valid curve is retained on reload, defaults are used at startup, the warning is reported, and the file remains available for correction.
- ANT consistently means raw physical mast strength. The monitor shows amplifier/elevation-adjusted strength separately.
- Monitor link distance is labelled Radio distance, including normal dimensional/propagation scaling before terrain. The ambiguous Mapped label is removed.
- Empty antenna indexes bypass block-change key allocation and lookup.
- Quiet direct-inventory card removal now saves and notifies the attached Relay immediately. Hoppers remain blocked.
- Removed unused display fields/imports and corrected stale compatibility/packaging documentation.
- Added an editorial pack with all addon text, ordered handbook content, block/item recipes, editable model resources and labelled native reference captures for 0.9r2 review.

The 0.7.6 compass behavior remains: TX-only binding uses the displayed coarse direction and rounded distance; gaining RX does not change the saved compass, and explicit rebinding can give an exact target.

RF formulas, decay, terrain curve and routing selection are unchanged. Directional remains usable with Simple Radio alone. Foci API remains 1, so existing compatible Foci releases are not forced to update by this core version change. Production JARs exclude addon tests and preview tooling; prototype copies are identical to release JARs.

See `validation/0.9.0-rc.1/` for the completed build and server-test evidence. Automated synthetic-packet tests do not establish live microphone playback quality.

Validation passed: **111 unit tests** (89 core, 5 Foci, 17 Directional), **95 core server tests**, **141 combined server tests** and **21 Directional-only server tests**. Production verification confirms Java 17 classes, module boundaries, Foci API 1 and byte-identical prototype copies with no addon test classes.
