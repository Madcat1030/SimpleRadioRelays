# Simple Radio Repeaters 0.4.4

Fixes the nearly black catalyst and upgrade inserts on the Relay. Their light now comes from the block immediately in front of the Relay, where the inserts are visible, instead of inside its opaque housing. They still follow normal sky and block lighting.

The correct catalyst was already being selected; the darkness made amethyst difficult to recognize. Catalyst selection, synchronization, models, card rendering and gameplay are unchanged. The runtime JAR differs from 0.4.3 only in `RelayRenderer.class` and the version metadata.

Replace the previous addon JAR on both server and clients. Existing 0.4 worlds and configuration work directly. This prototype targets Fabric Minecraft 1.20.1, Java 17+, and Simple Radio 1.20.1-4.5.7.9.

## Validation

- Build and all 51 unit tests pass.
- A native client joined a production Fabric server running the exact prototype JAR with JVM verification enabled.
- Checked blackstone, amethyst and all three upgrades, including the dark-housing/light-front condition.
- Removed blackstone and installed amethyst through ordinary socket interactions; the correct item and model reached the client.
- Captured the actual in-world production renderer under daylight.
- All 625 retained methods in 72 classes match the full development build byte for byte. The JAR contains no addon test fixtures or temporary visual QA mod, and adds no obfuscation.

The full 0.4.3 GameTest baseline is retained in the source bundle and was not rerun for this rendering-only patch. Its 60 addon tests pass; four previously recorded native headless failures remain unresolved. Live microphone playback was not part of this visual check.
