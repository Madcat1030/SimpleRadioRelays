# Simple Radio Repeaters 0.5.4

Relays now transmit and receive at the centre of the core directly below their resolved antenna, rather than at the relay box. Copper-wire extensions use the remote antenna core. The position is saved with the network snapshot, retained while wire/chunk restoration is incomplete, and used for relay links, endpoint distance, carrier origins and dimensional-focus projection. Relay ownership, bank mounting and dish association still use the box position. List/diagnostic output includes the antenna base.

Existing saved towers retain their previous origin until their next successful antenna scan. Loaded towers refresh on the normal 200-tick cycle; `/radiorepeaters refresh` also requests a check. Visiting previously unloaded towers allows their new origin to be resolved without force-loading any antenna chunks.

Gameplay keeps the numeric link adapter. New development-only parity tests compare it to real Simple Radio `shouldRouteTo`, `prepareSource`/`travel`, native tower power and `computeSeverity`. They include AM/FM, all standard dimensions, several sender types, weak incoming power, both loss methods, the reception floor, safe range-edge margins, coordinates near ±1,000,000, and cross-dimensional travel failure after a successful reach check. Foci tests exercise the native Nether hooks and noise floor as well as the adapter. Prototype jars exclude these tests.

Install matching 0.5.4 core and Dimensional Foci packages if using the expansion. Directional Antenna remains 0.2.6. Simple Radio is still pinned to 1.20.1-4.5.7.9. An unchanged version string cannot protect against an upstream binary being replaced under that version; the validation evidence identifies the jar actually tested.

Validation commands and results are recorded in `validation/0.5.4/README.md`.
