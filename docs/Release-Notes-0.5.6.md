# Simple Radio Repeaters 0.5.6 / Directional Antenna 0.2.7

The handbook is now **Simple Radio Relays**, with the revised player-facing guide. Its chapters cover the first relay, Relay block, Repeater Bank, Transponder, Frequency Card, amplifiers and troubleshooting. The first-tower checklist includes the required catalyst, the duplicate Transponder contents entry is removed, and the Transponder description correctly introduces three modes.

Crafting illustrations appear at the requested points. They use synchronized server recipes rather than copied ingredient lists, show shaped or shapeless layouts and output counts, cycle ingredient alternatives, and support item tooltips. The native book reader retains its arrows, keyboard navigation and clickable links. Covers and contents can now span multiple pages without overflowing.

## Addon-owned chapters

Core no longer stores the Foci or Directional chapters. Each addon ships its own JSON definition and translations. The reader discovers installed chapter resources when opened, then rebuilds the contents and page links. Existing handbook items update without recrafting. Directional Antenna remains independent of Repeaters; its optional handbook contribution is data only.

[Handbook-Extensions.md](Handbook-Extensions.md) documents the resource format for other addons and resource packs.

## Transponder modes

The center panel cycles **Simplex → Duplex → Multicast → Simplex**. The display shows a one-way arrow, two-way arrows or an asterisk respectively. A left RX card and at least one right TX card are required in every mode.

- Simplex retains left-to-right conversion.
- Duplex retains conversion between the left channel and either right channel; the right channels are not directly cross-connected.
- Multicast broadcasts each incoming selected channel onto every selected channel, including its unchanged channel. Duplicate frequencies collapse to one port per input/output pair, and existing frame deduplication still permits only one delivery to each receiver on a channel.

Normal direct reception on a user's own channel still works in every mode. To preserve a channel through a Simplex/Duplex Transponder itself, put that channel on both sides as before. Multicast explicitly repeats all selected channels.

Modes survive client synchronization, block reload and saved virtual routing records. Existing Simplex/Duplex data retains its previous setting. Changing mode publishes new relay settings through the existing invalidation mechanism.

## Wide-angle reception

The Wide-Angle Receiver Array now gives 66% of the shared maximum extra reception bonus through ±45°, 44% at ±60°, 22% at ±75° and zero at ±90°. Each shoulder fades smoothly. The Parabolic Receiver remains 100% through ±30°, 50% at ±45° and zero at ±60°. Ordinary reception outside the beam and strongest-bonus selection are unchanged; outgoing power is unaffected.

## Installation

Replace core with 0.5.6, Dimensional Foci with matching 0.5.6 if installed, and Directional Antenna with 0.2.7 if installed. Dependencies and Minecraft target remain unchanged. Existing handbook items, cards, inventories and legacy Transponder modes remain usable.

The 0.5.5 `/radiorepeater test start`, `start <seconds>` and `stop` diagnostics remain available. No delivery-plan cache or packet-rate optimization has been added.
