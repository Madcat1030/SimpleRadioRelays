# Repeaters 0.6.0

- Added Off, Relay Mapping (fresh-install default), and Full configuration modes. Off restores 0.5.8 propagation and disables the complete environmental model.
- Added Overworld relay terrain attenuation using sparse height samples from Minecraft's generator, without loading or generating world chunks.
- Added persistent, bounded relay profiles shared across channels, RF graph rebuilds and both directions.
- Recorded verified mast tips separately from the antenna base. Growth and trimming update clearance on the normal 200-tick boundary; unloaded/incomplete antennas retain verified information.
- Added a capped elevation bonus to the relay antenna contribution.
- Added optional terrain attenuation for ordinary device links, disabled by default. Endpoints within 256 blocks are exempt; distant active pairs resample only after an endpoint moves more than 16 blocks. Device paths are not saved or retained as profiles.
- Added terrain controls, handbook guidance and profiling counters. Engine sampling is queued and budgeted outside audio routing.

Use the matching Dimensional Foci 0.6.0 build if Foci is installed. Its gameplay is unchanged. Directional Antenna 0.2.9 remains compatible and standalone.

Existing relay inventory, channels and wiring remain valid. Older saved records acquire a verified mast tip on their next loaded scan; visit older towers once to bring them into the terrain model. New relay links can take a sampling cycle to become available. See [terrain settings](Terrain-and-Elevation.md) for defaults, commands and the surface-model limits.
