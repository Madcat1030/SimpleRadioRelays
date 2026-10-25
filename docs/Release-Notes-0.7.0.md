# Repeaters 0.7.0

Adds the Relay Monitor: a passive single-card attachment that plugs into the top of a Relay instead of a Repeater Bank or Transponder. Its overview reports antenna coordinates/strength, direct TX/RX counts and other active nodes on the selected frequency and modulation. Heard towers show coordinates, distance and terrain extra distance. TX-only pages show a 16-point bearing and approximate distance instead of coordinates.

Use an empty hand on the screen to cycle pages. Use a compass to mark the displayed destination, or this tower from the overview. Binding consumes one compass from a stack, supports an offhand compass with an empty main hand, preserves other item data and needs no lodestone or target chunk load. Exact TX-only coordinates are transmitted only when explicitly written to the compass.

Monitor cards are excluded from forwarding snapshots, including controller reconciliation and reload. The new observer uses existing radio and terrain calculations and caches results by card and network revision. Existing relay routing math, terrain settings, antenna scans, beam profiles and dimensional behavior are unchanged.

Includes the monitor model, one visible card, screen rendering, recipe, tooltips, correct harvesting tags/loot and an in-game handbook chapter. Model geometry uses integer voxel units and one texel per unit. Production artifacts contain no test or native-preview classes.

Dimensional Foci 0.7.0 updates compatibility metadata only. Directional Antenna remains 0.2.9 and does not require Repeaters.
