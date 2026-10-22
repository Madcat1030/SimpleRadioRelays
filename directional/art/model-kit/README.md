# Directional Receiver model kit — 0.2.5

Open **Parabolic-Receiver.bbmodel** for the solid stepped default or **Wide-Angle-Receiver.bbmodel** for the array. Both active models use whole model units and one texture pixel per model unit, or 16 pixels per block. Long members are subdivided rather than compressing texture pixels. Interior and duplicate surface portions are omitted; some editable cubes therefore intentionally have only selected faces enabled.

The earlier models remain editable:

- **Parabolic-Receiver-Mesh.bbmodel** — the 0.2.3 stepped mesh.
- **Parabolic-Receiver-Octagonal.bbmodel** — the solid eight-sided version.
- **Parabolic-Receiver-Refined.bbmodel** — the finer round version.
- **Wide-Angle-Receiver.bbmodel** — the wide-angle array.

Each project embeds its textures. In Minecraft, select one of the named appearance packs in Options → Resource Packs to use an earlier dish. Leave all three disabled for the solid stepped default. They are appearances of the same block. If several are active, ordinary resource-pack priority applies.

**Native-Antenna-Column.bbmodel** and **Native-Antenna-Vertical.bbmodel** are exact supported native mast references. Original Java JSONs, PNGs and the GPL license are under `native-reference/`. Top variants are excluded because the receiver needs another mast section above its support. The original unmodified mesh JSON is under `original-reference/`.

## Editing and export

One block is 16 model units. North (negative Z) is the base facing; the mast is toward positive Z. The center block is 0–16 on each axis. Both receivers reserve X = −16…32 and Y = −16…32, a 3×3 plane. Visual edits do not alter reserved cells or collision.

The three editable groups are **Shared mast rings**, **Fixed receiver support** and **Swivelling head**. Keep shared rings and their bolts named with **mount_clamp_** or **mount_bolt_** prefixes. Other stationary parts use **mount_**. Everything else swivels about **[8, 8, 8]**. Leave the groups at 0° for export. The build splits the Java model into `_clamps`, `_mount` and `_head` layers. Only the receiver assigned `brackets=true` renders the rings. Both receivers retain their support arms. The four mounting directions and seven aims use ordinary baked block models.

Export a Minecraft Java block/item model into:

- Default dish: `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json`
- Earlier dishes: the same directory's `parabolic_receiver_mesh.json`, `parabolic_receiver_octagonal.json` and `parabolic_receiver_refined.json`.
- Array: `wide_angle_receiver.json` in that directory.

Preserve texture references and place PNGs in the corresponding `textures/block/` directory. Alternate appearance packs live under `directional/src/main/resources/resourcepacks/`; update their full, `_clamps`, `_mount` and `_head` model JSONs together. The model-kit ZIP contains the Java assets, textures and all three optional packs. Native mesh remains in the `simpleradio` namespace.

Run `python3 directional/tools/make_receiver_assets.py` to rebuild all receivers and their shared mounts, final surface trimming and editable projects. `model_surfaces.py` performs the offline surface/UV work; `verify_distribution.py` inspects transformed polygons in the packaged assets. These tools are outside runtime source sets. Generation overwrites manual edits. `make_model_kit.py` rebuilds only native mast references.

## Aiming and reception

Empty-hand right-click on any receiver part aims clockwise; sneak-right-click aims counterclockwise. The seven stops are −45°, −30°, −15°, 0°, +15°, +30° and +45°. The clamp stays fixed. Leave clearance for the visible sweep.

| Receiver | Primary arc | Extra reception gain | Fade ends |
| --- | --- | --- | --- |
| Wide-angle array | 135° (±67.5°) | Half the parabolic's extra gain | ±90° |
| Parabolic | 90° (±45°) | Full extra gain | ±75° |

The strongest applicable bonus wins; dishes do not stack. Visual alternatives have identical reception behavior.

The 0.2.5 PNGs are raw native Minecraft model-preview screenshots, including both receivers, shared mounts, aiming positions and all three pack reloads. Capture source and focused shared-mount tests are under `directional/validation/0.2.5/` in the source archive.
