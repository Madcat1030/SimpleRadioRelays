# Repeaters 0.7.1

Fixes configuration loading, terrain status housekeeping and Full-mode diagnostics. The Relay Monitor introduced in 0.7.0 is included unchanged.

- Valid settings now take effect even when the config file cannot be rewritten. A failed normalization save produces a warning; unreadable or malformed JSON still fails to load and keeps the current settings.
- Pending terrain display markers are cleared when the relay graph is invalidated, then restored only for paths considered by the new graph. This prevents a full sampling queue followed by a range change from leaving a relay permanently showing SCAN or CHECK. Existing results and queued work are retained. Building a different channel does not clear another cached channel's markers.
- Read-only diagnostic graph checks no longer modify mapping markers. Full-mode device diagnostics now report the same ordinary-propagation fallback as live routing when the retained-device safety limit is reached.
- Configs containing the retired `clearance` or `buriedLoss` keys emit one migration warning pointing to the replacement settings.
- The handbook now explains that SCAN means initial mapping without usable links or prior terrain results. CHECK can also appear while a new path is mapped if other working links are available; that new path still waits for its first measurement.

The 200-tick mast/profile publication cadence, the 128-job relay queue, terrain attenuation curve and 0.5.x upgrade behavior are unchanged. No routing optimization or new attenuation rule is included.

Dimensional Foci 0.7.1 changes compatibility metadata only. Directional Antenna remains 0.2.9 and needs no replacement.
