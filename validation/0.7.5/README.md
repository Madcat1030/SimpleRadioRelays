# 0.7.5 validation

Passed 105 unit tests, 91 core server GameTests and 137 combined server GameTests with Foci and Directional installed. The new server fixtures use real native transmitter/receiver/source objects to cover the base-power progression, disabled parity, actual reach/travel, unchanged handheld/Radio/custom types, relay upgrade/decay behavior and cached saved-only links. They request no missing chunks.

Existing long-range saved-chain and native-wire round-trip fixtures now use suitable antenna strengths for the new default balance. Edge fixtures calculate their receiving allowance with the enabled rule. Their routing, wire, frame identity, echo and restoration assertions remain intact.

Only configured station base contributions change. Antenna gain, catalyst/hop behavior, travel/interference formulas and terrain sampling are retained. Foci API 1 is unchanged; Foci 0.7.3 and Directional 0.2.9 remain byte-identical. The prototype verifier excludes test/preview/host-agent classes from release jars. These automated packet tests do not establish live microphone playback quality.
