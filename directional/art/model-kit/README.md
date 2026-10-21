# Directional Receiver model kit — 0.2.2

Open **Parabolic-Receiver.bbmodel** for the chunky default, **Parabolic-Receiver-Refined.bbmodel** for the retained finer alternate, or **Wide-Angle-Receiver.bbmodel** for the array. Each project embeds its textures. Both dishes use five 16×16 materials at one texture pixel per model unit.

The chunky dish has an octagonal copper rim, three broad bowl depths, a substantial feed arm and whole-unit wrap-around clamps. Its coordinates, dimensions, UVs and rotation origins are integral; no part is thinner than one model unit, and native element rotations are only 0° or ±45°. The refined alternate retains its original finer bowl, rim, feed rails and clamps. Both appearances are included in the mod: enable **Simple Radio: Refined Parabolic Receiver** in Options → Resource Packs to select the finer style, or leave it disabled for the chunky default. This changes client appearance for the same block.

**Native-Antenna-Column.bbmodel** and **Native-Antenna-Vertical.bbmodel** are exact supported native mast references. Their original Java JSONs, PNGs and GPL license are under `native-reference/`. Top variants are excluded because a receiver requires another antenna section above its support. Keep the reference projects separate from the receiver export.

## Export and aiming

One block is 16 model units. The base orientation faces North (negative Z); the mast is behind the center toward positive Z. The center block is 0–16 on each axis. Both receivers reserve X = −16…32 and Y = −16…32, a 3×3 plane. The wide-angle array's visible face is approximately two blocks wide and two high. Visual edits do not change reserved cells or collision.

Keep all fixed cubes named with a **mount_** prefix. The original parabolic also accepts **mast_clamp** and **mounting_arm**. All other cubes swivel about **[8, 8, 8]**. Keep the fixed and swivel groups at 0° in the base export. The build splits the single editable Java JSON into fixed and rotating layers and generates all four mounting directions and seven aim settings. Native model baking caches the rotated quads; no per-frame block-entity renderer is involved.

Export the receiver alone as a Minecraft Java block/item model. In the full source workspace replace:

- Array: `directional/src/main/resources/assets/simpleradio_directional/models/block/wide_angle_receiver.json`
- Dish: `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json`
- Refined reference: `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver_refined.json`. The built-in alternate pack is under `directional/src/main/resources/resourcepacks/refined_parabolic/`; its full, `_mount` and `_head` model files must stay together when updating the alternate.

Retain texture references and place edited PNGs in the corresponding `textures/block/` directory. The model-kit archive includes all three current Java JSONs and the five PNGs under `assets/`, plus the optional pack under `resourcepacks/`. You can instead return an edited `.bbmodel` for integration. `directional/tools/make_blocky_parabolic.py`, `make_parabolic_receiver.py` and `make_wide_array.py` reproduce these assets; do not run them over manual edits unless restoring this version. The reference extractor `make_model_kit.py` only rebuilds native mast references.

In game, empty-hand right-click on any receiver part aims clockwise; sneak-right-click aims counterclockwise. Settings are −45°, −30°, −15°, 0°, +15°, +30° and +45°, without wrapping. Clamps remain fixed. Leave room for the visible sweep.

## Reception

| Receiver | Full primary arc | Additional gain | Smooth fade ends |
| --- | --- | --- | --- |
| Wide-angle array | 135° (±67.5°) | Half the parabolic's extra gain | ±90° |
| Parabolic | 90° (±45°) | Full extra gain | ±75° |

The strongest applicable bonus wins; receivers do not stack. These patterns alter reception allowance, preserving ordinary reception and inherited audio quality. Geometry changes alone do not change the reception pattern.

The preview PNGs are unretouched screenshots from the actual Minecraft 1.20.1 client renderer, with the real texture atlas and native mast. They are native model sheets, rather than placed-world scenes. Both appearances are rendered together in the comparison sheet. Capture source and checks are in the full source archive under `directional/validation/0.2.2/`; earlier array captures remain under `0.2.0/`.
