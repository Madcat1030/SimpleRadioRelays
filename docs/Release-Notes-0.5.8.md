# Repeaters 0.5.8 / Dimensional Foci 0.5.8 / Directional Antenna 0.2.9

Cutting an antenna-extension wire now invalidates affected relay connections immediately, including saved towers whose relay box is unloaded. Leaving the area or saving immediately after a cut cannot preserve the former connection. Reconnecting a wire is checked after native socket attachment, without waiting for the regular ten-second scan. Ordinary chunk unloading still retains verified connections.

Simple Radio's automatic missing-endpoint timeout also pauses for verified relay antenna extensions while their saved dependencies are unloaded. This prevents a wire that remains loaded from expiring solely because its other endpoint unloaded. Deliberate cuts and native expiry with all dependencies available still work normally.

Directional now preserves verified dish-to-mast bindings while native wire entities restore their socket lists. Previously, an early empty traversal could overwrite that binding and remove the reception bonus from a saved relay. At long distances this could break an otherwise valid link. Real wire cuts clear the affected reception immediately. This works with ordinary Simple Radio receivers as well as Repeaters; Directional still runs independently.

All commands now use `/radiorepeaters`. The duplicate `/radiorepeater` alias has been removed, and profiling messages use the remaining name.

Replace the installed core with 0.5.8 and Directional with 0.2.9 on server and clients. If using Dimensional Foci, use the matching 0.5.8 build; its behavior is unchanged. Existing worlds, cards and hardware need no conversion. If an older build already saved a missing dish binding, visit the affected towers once so their loaded antenna scans can reconstruct it.

This release does not change signal math, dish beams, catalyst decay, recipes, models, or packet routing cadence. Wire notifications use Fabric events; one narrow mixin guards native missing-endpoint expiry. Unchanged antenna scans still keep the cached graph.

See [validation and capture analysis](../validation/0.5.8/README.md) for the regression tests and the three supplied captures.
