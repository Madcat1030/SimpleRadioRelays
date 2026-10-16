# Simple Radio Repeaters 0.4.0

The Relay gains an attachment socket left of the catalyst. Install a Nether Focus, End Focus or Relay Amplifier; the catalyst and a supported antenna remain mandatory.

Foci add mapped transmit/receive pockets only while Simple Radio's native cross-dimensional radio is disabled. Nether injection is at roof Y, End injection at bottom Y. The End has no 500-block cap: native dimensional attenuation decides reach. Two projected pockets need a physical target-world relay to communicate through that world. With native cross-dimensional radio enabled, foci are ignored and physical relays use the mod's normal XYZ coordinate scaling and attenuation.

Amplifiers restore the local catalyst-scaled departure ceiling by resetting decay count to 1. Total visits still increment, and inherited audio degradation remains. The search tracks both counts, retains useful amplifier detours and caches exact numeric alternatives.

A three-card Radio Transponder replaces the Bank atop a Relay. Simplex converts the left input to selected right outputs; duplex also converts either right input back to the left. A tuned pair is required. AM/FM conversion, main-channel branching and later relay hops are supported. Front card controls and a center direction switch match the physical model. The existing six-card Bank artwork and behavior remain unchanged.

New recipes use the requested focus ingredients, Ancient Debris for netherite ore, and native receiving/transmitting modules in the Transponder. Neither crafting a Transponder with a catalyst block nor installing an attachment fills the Relay's separate catalyst socket.

Standalone build: passed. Unit tests: 48/48. Addon Minecraft tests: 50/50. Seven native tests also run; the same four names fail as in the earlier addon-disabled headless control. Their cause is unresolved. Native model captures and separated reports are included. Tests use synthetic encoded bytes; actual multiplayer voice playback was not exercised.

The saved network remains schema 3 with new optional attachment/policy fields. Existing 0.3 towers do not reset. The temporary preview mod is excluded from the release JAR.
