# Parabolic Receiver model kit

Open **Parabolic-Receiver-Placeholder.bbmodel** in Blockbench to edit the dish. Open either **Native-Antenna-Column.bbmodel** or **Native-Antenna-Vertical.bbmodel** for the two eligible native mast styles. Every project embeds its exact 16×16 source textures; external texture paths are unnecessary.

The references are Simple Radio 1.20.1-4.5.7.9 geometry, copied without modification. Their original Java model JSON and PNG files are under `native-reference/`, with the base mod's GPL license. Top variants are excluded because the dish must have antenna above its supporting mast section.

One block is 16 model units. The dish faces North (negative Z), reserves a 48×48-unit vertical plane around its center, and mounts to a mast one block behind it (positive Z). The center block is 0–16 on each axis; the reserved plane is X −16…32, Y −16…32. The mounting arm may extend behind it into the mast block.

Export the **dish project only** as a Minecraft Java block/item model. Replace `directional/src/main/resources/assets/simpleradio_directional/models/block/parabolic_receiver.json` in the source workspace. Keep the origin and texture references. You can also supply the edited `.bbmodel` for integration.

Visual edits do not change the 3×3 reservation or collision cells. The four mounting sides use blockstate rotations. The reflector also swivels in seven 15° steps from −45° to +45° relative to that mount. Reception stays full within 45° of the selected aim, drops to 25% extra gain at 60°, and reaches zero extra gain at 75°.

The **Mount** group is stationary and the **Reflector** group swivels. Keep the existing fixed cubes named **mast_clamp** and **mounting_arm**, or name replacement fixed cubes with a **mount_** prefix. All other cubes rotate around the reflector pivot **[8, 8, 8]**. Keep both groups at 0° when exporting; runtime handles the aim angles. The build automatically splits the editable Java model into fixed mount and swivel layers, and caches angled quads when resources load. Keep the feed and reflector together when redesigning the dish. Leave room for the visual sweep; selection/collision retains the original fixed 3×3 plane.

In game, right-click any dish part with an empty hand to aim clockwise; sneak-right-click to aim counterclockwise. The end stops do not wrap. Old dishes start straight out from the mast.
