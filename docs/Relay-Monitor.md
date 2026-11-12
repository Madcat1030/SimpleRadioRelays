# Relay Monitor

The Relay Monitor is a passive attachment for the top of a Relay. It replaces a Repeater Bank or Transponder and never forwards audio. The underlying Relay still needs a catalyst, a valid core and an antenna.

Insert one tuned Frequency Card through the front. The selected frequency and AM/FM modulation determine every prediction. An empty slot or untuned card shows only: **Insert tuned card for Relay diagnostics**.

## Overview and pages

The overview shows the antenna base coordinates and dimension, physical mast strength (ANT), effective antenna strength including amplifier and elevation bonuses, TX and RX counts, and other active nodes on the channel. ANT is the raw score used for base-power recovery through ANT 16.

- **TX:** configured nodes this tower can directly transmit to.
- **RX:** configured nodes this tower can directly hear.
- **Other nodes:** operational nodes on this frequency/modulation that are not heard directly. This includes TX-only nodes and nodes outside direct reach. It does not imply a working route through the relay network.

A TX/RX node counts in both direction totals. Nodes are counted once even if a Transponder has several matching ports. The monitor itself is not an active forwarding node. Saved unloaded nodes count when persistent unloaded relays are enabled.

Click the centered blue ring to open the overview. From the overview, right-click the screen with an empty hand to cycle through the direct-link pages and back to the map. The direct-link pages list heard towers first:

| Page | Information |
| --- | --- |
| TX/RX | Destination antenna coordinates, dimension, radio distance, terrain extra-distance percentage |
| RX only | The same details, with RX only identifying the missing return direction |
| TX only | Dimension, a 16-point bearing such as SSE or WNW, and approximate distance; no exact coordinates or terrain figures |

TX-only distances round to the nearest 500 blocks. Below 500 blocks they round to 50 blocks to avoid reporting a zero-distance target. Exact TX-only coordinates are omitted from client display data, not merely hidden by the renderer.

**Radio distance** includes the selected link's dimensional and propagation scaling, before terrain extra distance. In an ordinary Overworld link it equals direct distance. Coordinates identify the physical destination antenna. Interdimensional links do not acquire Overworld terrain attenuation.

Click the inserted card with an empty hand to remove it. Card actions work only through the front face. Hoppers have no inventory access; direct container integrations remain available as with existing card banks.

## Network map

The network map opens when you insert a tuned card. It is north-up, with the monitor's own antenna marked by a blue ring. The coordinates along the bottom belong to the centered tower.

| Marker | Meaning |
| --- | --- |
| Green ring | The monitor can reach and hear this tower. |
| White ring | The monitor can hear this tower. |
| Orange ring | The first unheard tower on an outbound branch. Its footer shows a rounded distance and bearing from the last known tower. |

Solid lines show selected outbound and return legs. Dotted lines connect orange estimates to the last known tower that reached them. The map follows the tuned channel in the current dimension for up to 12 hops each way, subject to the server's lower limit. Terrain, relay decay, and Ender resets affect reach.

Right-click a ring with an empty hand to center it and show towers within two drawn links. Colors and links still describe communication with the monitor's own antenna. Right-click the centered ring to open the tower's detail page. Click that page to return to the focused map. Click open space on a focused map or the blue ring to return to the full map.

## Compass binding

Use an ordinary compass on the overview, a direct-link page, or a tower detail page to bind it without changing the page:

- A TX/RX or RX-only page binds to that tower's actual antenna base.
- A TX-only page binds to the centre of its displayed 16-point bearing at the displayed rounded distance. Scaled radio distances are converted to blocks in the destination dimension. The target height uses your antenna's height, clamped to that world's limits, rather than revealing the remote tower's height.
- The overview binds to this tower.
- One compass is bound at a time; remaining items in a stack stay unchanged.
- An offhand compass works when the main hand is empty.

The compass uses vanilla lodestone targeting without requiring a lodestone. It keeps the fixed coordinate through saves and reloads and does not load the target chunk. It works in the target dimension. Moving or rebuilding an antenna does not update an already-bound compass; bind it again if needed. A compass bound from a TX-only page stays approximate when the tower later becomes hearable. Rebinding while it is heard replaces the estimate with the exact antenna location. Existing bound compasses are not rewritten. Repeating the same approximate binding gives the same point, rather than random offsets.

## What the numbers mean

Predictions use fresh first-relay departure power, the installed catalyst/amplifier, directional reception and the existing dimensional policy. Input and output channel roles are tested separately for Transponders. A message already weakened by earlier relay hops can fail a nominal direct link.

Survey results are reused while the channel, antenna and network revision remain unchanged. Updates run on the server and at most once per second during ordinary ticking, with immediate checks for card changes and screen interactions. The map is calculated only when it is displayed. The renderer only draws the synchronized page; it performs no network or terrain work.

Surveying reuses the existing cached terrain profiles and budgeted sampling queues. Unknown paths show a Surveying count on the monitor. Survey requests do not add operational SCAN/CHECK markers to either relay. Established paths keep their previous environmental penalty during remapping. No survey loads remote chunks, emits a packet or adds a forwarding port.

## Recipe

| | | |
| --- | --- | --- |
| Iron ingot | Glass | Iron ingot |
| Receiving Module | Compass | Receiving Module |
| Iron ingot | Simple Radio copper wire | Iron ingot |

A stone-tier or better pickaxe harvests the monitor. Breaking it drops its card and, with the proper tool, the block.
