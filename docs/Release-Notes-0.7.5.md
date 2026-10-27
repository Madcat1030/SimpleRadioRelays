# Repeaters 0.7.5

Block Transmitters, block Receivers and relay towers now start at 50% of their configured base power. Physical antenna strength restores it linearly to 75% at ANT 8 and 100% at ANT 16. Their normal antenna bonuses are added throughout, and continue growing above ANT 16. Handhelds, placed Radios and other frequencing types are unchanged.

The new `antennaDependentBasePower` flag defaults to `true` in `config/simpleradio-repeaters.json`. Set it to `false` to restore the previous behavior. It is independent of terrain mode. Restart after editing, or use the existing `/radiorepeaters terrain reload` command to reload the config and refresh RF graphs without discarding terrain profiles.

Native endpoint power and virtual relay power use the same rule. Amplifier/elevation antenna bonuses remain intact; catalyst efficiency and hop decay apply afterward. Relays still require a valid antenna and catalyst. The in-game guide and diagnostics describe the new balance.

Use the existing Dimensional Foci 0.7.3 and Directional Antenna 0.2.9 jars. Foci API 1 is unchanged.

## Validation

Passed 105 unit tests, 91 core server tests, and 137 combined server tests with the existing Foci and Directional addons. Production jars contain no test classes; travel math and terrain sampling are unchanged.
