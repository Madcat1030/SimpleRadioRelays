# Directional Receiver model kit — 0.2.4

Open **Parabolic-Receiver.bbmodel** for the solid stepped default. It has the same 32-panel outline and front bowl profile as the previous mesh, with a connected opaque shell, pale metal fronts, darker backing and a steel frame. Feed and wrap-around clamp geometry are preserved; two hinges move four units behind the shell to prevent coplanar flicker. Every default part uses whole model units, with no part thinner than one unit. All four dish appearances use one texture pixel per model unit, or 16 pixels per block.

The earlier models remain editable:

- **Parabolic-Receiver-Mesh.bbmodel** — the 0.2.3 stepped mesh.
- **Parabolic-Receiver-Octagonal.bbmodel** — the solid eight-sided version.
- **Parabolic-Receiver-Refined.bbmodel** — the finer round version.
- **Wide-Angle-Receiver.bbmodel** — the wide-angle array.

Each project embeds its textures. In Minecraft, select one of the named appearance packs in Options → Resource Packs to use an earlier dish. Leave all three disabled for the solid stepped default. They are appearances of the same block. If several are active, ordinary resource-pack priority applies.

**Native-Antenna-Column.bbmodel** and **Native-Antenna-Vertical.bbmodel** are exact supported native mast references. Original Java JSONs, PNGs and the GPL license are under `native-reference/`. Top variants are excluded because the receiver needs another mast section above its support. The original unmodified mesh JSON is under `original-reference/`.

## Editing and export

One block is 16 model units. North (negative Z) is the base facing; the mast is toward positive Z. The center block is 0–16 on each axis. Both receivers reserve X = −16…32 and Y = −16…32, a 3×3 plane. Visual edits do not alter reserved cells or collision.

Keep stationary cubes named with a **mount_** prefix. Everything else swivels about **[8, 8, 8]**. Leave both groups at 0° for export. The build splits the editable Java JSON into fixed and rotating layers and generates the four mounting directions and seven aim settings. Quads are cached at resource bake time; no per-frame block-entity renderer is used.

Export a Minecraft Java block/item model into:

- Default dish: `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json`
- Earlier dishes: the same directory's `parabolic_receiver_mesh.json`, `parabolic_receiver_octagonal.json` and `parabolic_receiver_refined.json`.
- Array: `wide_angle_receiver.json` in that directory.

Preserve texture references and place PNGs in the corresponding `textures/block/` directory. Alternate appearance packs live under `directional/src/main/resources/resourcepacks/`; update their full, `_mount` and `_head` model JSONs together. The model-kit ZIP contains the authored Java assets, textures and all three optional packs. Native mesh remains in the `simpleradio` namespace.

`tools/make_solid_parabolic.py` authors the default from the archived mesh model. The other generators retain their named appearances. Running a generator overwrites manual edits. `make_model_kit.py` rebuilds only the native mast references.

## Aiming and reception

Empty-hand right-click on any receiver part aims clockwise; sneak-right-click aims counterclockwise. The seven stops are −45°, −30°, −15°, 0°, +15°, +30° and +45°. The clamp stays fixed. Leave clearance for the visible sweep.

| Receiver | Primary arc | Extra reception gain | Fade ends |
| --- | --- | --- | --- |
| Wide-angle array | 135° (±67.5°) | Half the parabolic's extra gain | ±90° |
| Parabolic | 90° (±45°) | Full extra gain | ±75° |

The strongest applicable bonus wins; dishes do not stack. Visual alternatives have identical reception behavior.

The 0.2.4 PNGs are raw native Minecraft model-preview screenshots, including a mesh comparison and all three pack reloads. Capture source and checks are under `directional/validation/0.2.4/` in the source archive.
