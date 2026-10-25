# 0.6.1 validation

- 91 JUnit tests passed: 72 core, 2 Foci and 17 Directional.
- 76 core-only Minecraft server tests passed.
- 122 combined Minecraft server tests passed with Repeaters, Foci and Directional.

The regressions exercise 625 simultaneously active native device pairs, native and relay preflight rejection, a 1,035-profile mesh, full saved-data roundtrips, reference pruning, replacement resolutions, a real datapack reload, malformed config command failure, and physical SCAN/READY/CHECK display transitions. Existing remapping retains its committed penalty; mast height changes reuse sampled ground. Unit coverage checks all requested above-ground/underground anchors, weighted mountain attenuation, extra-distance scaling, adaptive work budgets and config migration/validation.

The first UI fixture checked its display before the next block-entity update; the final fixture waits for the normal display transition. Only the final successful core XML and daemon log are retained here. The malformed-config test intentionally logs one rejected JSON parse to verify failure handling.

Foci changes only fabric.mod.json; its seven classfiles are byte-identical to 0.6.0. Directional 0.2.9 is byte-identical to the previous release. All production classes target Java 17. Standard and prototype artifacts are identical, exclude test classes and use no custom obfuscation. Full source includes the separate development tests.

GameTests run in a native Minecraft server on the disposable Java 21 build host. They do not substitute for a live microphone playback test or establish a production server performance ceiling. Host-only build adapters are not shipped in the JARs.
