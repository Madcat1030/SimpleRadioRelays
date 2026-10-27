# Antenna power balance

Repeaters 0.7.5 enables an antenna-dependent base-power rule for Simple Radio's block Transmitters, block Receivers and virtual relay towers. Handheld transceivers, walkie-talkies, placed Radios and third-party radio types keep their original power.

| Physical antenna score | Base contribution | Antenna contribution |
| --- | --- | --- |
| 0 | 50% | Normal |
| 2 | 56.25% | Normal |
| 8 | 75% | Normal |
| 16 or more | 100% | Normal |

Between 0 and 16 the recovery is linear. Above 16 the base stays at 100%, while normal antenna bonuses continue growing. A relay still requires a verified positive antenna score, a valid catalyst and a configured forwarding module.

`power = configured base × (0.5 + clamp(raw ANT, 0, 16) / 32) + normal antenna contribution`

The transmitter uses its configured AM/FM base. The receiver uses its configured reception allowance. These are separate quantities: actual one-way reach depends on the transmitting device's power plus the receiving device's allowance, travel loss and any terrain/dimensional penalties. This does not impose a fixed listening radius on receivers hearing a powerful distant transmitter.

For example, with AM base power 4,400 and antenna aptitude 10, transmitter powers are 2,200 at ANT 0; 3,380 at ANT 8; and 4,560 at ANT 16. A receiver with base allowance 300 and aptitude 10 has 150, 305 and 460 respectively. Two matching bare stations therefore have about half their previous combined reach before other losses. The receiver still has an allowance that handhelds lack.

Recovery uses physical antenna strength. Relay amplifiers and elevation retain their full existing antenna bonuses; they do not turn a small physical mast into ANT 16 for base recovery. Catalyst efficiency and relay-count decay apply afterward, including the Ender Amplifier's existing decay reset. Directional reception and dimensional propagation compose with the resulting powers as before.

## Configuration

In `config/simpleradio-repeaters.json`:

```json
"antennaDependentBasePower": true
```

Set it to `false` to restore the previous station/relay base powers at every antenna score. The flag is independent of the terrain mode: terrain Off does not disable antenna balancing. Restart the server after editing, or use the existing `/radiorepeaters terrain reload` command, which reloads this complete config file. Reloading invalidates affected RF graphs while retaining measured terrain profiles.

No Simple Radio configuration or saved antenna score is rewritten. Foci API 1 is unchanged; existing Foci 0.7.3 and Directional 0.2.9 installations can remain installed.
