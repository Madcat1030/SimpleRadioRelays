# Directional Receiver model kit — 0.2.0

Open **Wide-Angle-Receiver.bbmodel** for the new array or **Parabolic-Receiver-Placeholder.bbmodel** for the original dish. Both projects embed their pixel textures. The wide-angle array uses four 16×16 materials and 52 native Java-model cuboids; its two shallow grilles, receiving element, ceramic insulator and swivel are actual block geometry.

**Native-Antenna-Column.bbmodel** and **Native-Antenna-Vertical.bbmodel** are exact supported native mast references. Their original Java JSONs, PNGs and GPL license are under `native-reference/`. Top variants are excluded because a receiver requires another antenna section above its support. Keep the reference projects separate from the receiver export.

## Export and aiming

One block is 16 model units. The base orientation faces North (negative Z); the mast is behind the center toward positive Z. The center block is 0–16 on each axis. Both receivers reserve X = −16…32 and Y = −16…32, a 3×3 plane. The wide-angle array's visible face is approximately two blocks wide and two high. Visual edits do not change reserved cells or collision.

Keep all fixed cubes named with a **mount_** prefix. The original parabolic also accepts **mast_clamp** and **mounting_arm**. All other cubes swivel about **[8, 8, 8]**. Keep the fixed and swivel groups at 0° in the base export. The build splits the single editable Java JSON into fixed and rotating layers and generates all four mounting directions and seven aim settings. Native model baking caches the rotated quads; no per-frame block-entity renderer is involved.

Export the receiver alone as a Minecraft Java block/item model. In the full source workspace replace:

- Array: `directional/src/main/resources/assets/simpleradio_directional/models/block/wide_angle_receiver.json`
- Dish: `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json`

Retain texture references and place edited PNGs in the corresponding `textures/block/` directory. The model-kit archive also includes the array's current JSON and four PNGs under `assets/`. You can instead return an edited `.bbmodel` for integration. `directional/tools/make_wide_array.py` reproduces the authored array resources, so do not rerun it over manual model edits unless you intend to restore this version.

In game, empty-hand right-click on any receiver part aims clockwise; sneak-right-click aims counterclockwise. Settings are −45°, −30°, −15°, 0°, +15°, +30° and +45°, without wrapping. Clamps remain fixed. Leave room for the visible sweep.

## Reception

| Receiver | Full primary arc | Additional gain | Smooth fade ends |
| --- | --- | --- | --- |
| Wide-angle array | 135° (±67.5°) | Half the parabolic's extra gain | ±90° |
| Parabolic | 90° (±45°) | Full extra gain | ±75° |

The strongest applicable bonus wins; receivers do not stack. These patterns alter reception allowance, preserving ordinary reception and inherited audio quality. Geometry changes alone do not change the reception pattern.

The two preview PNGs are unretouched screenshots from the actual Minecraft 1.20.1 client renderer, with the real texture atlas and native mast. They are native model sheets, rather than placed-world scenes. Capture source and checks are included in the full source archive under `directional/validation/0.2.0/`.
