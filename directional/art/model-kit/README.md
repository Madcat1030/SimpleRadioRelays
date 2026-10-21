# Directional Receiver model kit — 0.2.3

Open **Parabolic-Receiver.bbmodel** for the finished stepped mesh, **Parabolic-Receiver-Octagonal.bbmodel** for the retained solid eight-sided dish, **Parabolic-Receiver-Refined.bbmodel** for the finer round alternate, or **Wide-Angle-Receiver.bbmodel** for the array. Each project embeds its textures. The three parabolic appearances use 16×16 materials at one texture pixel per model unit.

The mesh default keeps the original 32-panel outline and concavity, with a continuous framed rim, copper binding straps, three feed supports and paired mast clamps. It uses the actual native `simpleradio:block/antenna_column` mesh and a new galvanized frame material. All default geometry is orthogonal and on whole model units, with no part thinner than one unit. The original unmodified mesh JSON is retained under `original-reference/`.

Both later solid designs remain included. Select **Simple Radio: Octagonal Parabolic Receiver** or **Simple Radio: Refined Parabolic Receiver** in Options → Resource Packs. Leave both disabled for the finished mesh. This is a client appearance choice for the same block. If multiple appearance packs are active, normal resource-pack priority applies.

**Native-Antenna-Column.bbmodel** and **Native-Antenna-Vertical.bbmodel** are exact supported native mast references. Their original Java JSONs, PNGs and GPL license are under `native-reference/`. Top variants are excluded because a receiver requires another antenna section above its support. Keep the reference projects separate from the receiver export.

## Export and aiming

One block is 16 model units. The base orientation faces North (negative Z); the mast is behind the center toward positive Z. The center block is 0–16 on each axis. Both receivers reserve X = −16…32 and Y = −16…32, a 3×3 plane. The wide-angle array's visible face is approximately two blocks wide and two high. Visual edits do not change reserved cells or collision.

Keep all fixed cubes named with a **mount_** prefix. The original parabolic also accepts **mast_clamp** and **mounting_arm**. All other cubes swivel about **[8, 8, 8]**. Keep the fixed and swivel groups at 0° in the base export. The build splits the single editable Java JSON into fixed and rotating layers and generates all four mounting directions and seven aim settings. Native model baking caches the rotated quads; no per-frame block-entity renderer is involved.

Export the receiver alone as a Minecraft Java block/item model. In the full source workspace replace:

- Array: `directional/src/main/resources/assets/simpleradio_directional/models/block/wide_angle_receiver.json`
- Dish: `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json`
- Octagonal reference: `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver_octagonal.json`, with its pack under `resourcepacks/octagonal_parabolic/`.
- Refined reference: `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver_refined.json`. The built-in alternate pack is under `directional/src/main/resources/resourcepacks/refined_parabolic/`; its full, `_mount` and `_head` model files must stay together when updating the alternate.

Retain texture references and place edited PNGs in the corresponding `textures/block/` directory. Native mesh remains in the `simpleradio` namespace. The model-kit archive includes all current Java JSONs/materials and both optional packs. You can instead return an edited `.bbmodel` for integration. The mesh authoring tool is `directional/tools/make_mesh_parabolic.py`; the other receiver generators retain their named alternates. Do not run generators over manual edits unless restoring this version. The reference extractor `make_model_kit.py` only rebuilds native mast references.

In game, empty-hand right-click on any receiver part aims clockwise; sneak-right-click aims counterclockwise. Settings are −45°, −30°, −15°, 0°, +15°, +30° and +45°, without wrapping. Clamps remain fixed. Leave room for the visible sweep.

## Reception

| Receiver | Full primary arc | Additional gain | Smooth fade ends |
| --- | --- | --- | --- |
| Wide-angle array | 135° (±67.5°) | Half the parabolic's extra gain | ±90° |
| Parabolic | 90° (±45°) | Full extra gain | ±75° |

The strongest applicable bonus wins; receivers do not stack. These patterns alter reception allowance, preserving ordinary reception and inherited audio quality. Geometry changes alone do not change the reception pattern.

The preview PNGs are unretouched Minecraft 1.20.1 client screenshots using the real texture atlas and native mast. They are model-preview sheets, rather than placed-world scenes. The comparison shows the finished mesh alongside its original reference; both retained solid alternates have native captures after real pack reloads. Capture source and checks are under `directional/validation/0.2.3/` in the source archive.
