# Repeaters 0.7.3 / Dimensional Foci 0.7.3

Removes Foci's exact core-release requirement. Core now advertises a Foci API number in its mod metadata, and Foci checks that number against its own requirement before game initialization. Matching API numbers allow different core and Foci release versions. Missing, malformed or mismatched API numbers stop loading with a message identifying the incompatible mod versions and required API.

Install both 0.7.3 jars once to adopt this scheme. Afterwards, keep this Foci jar across routine core updates while they retain Foci API 1. Only an incompatible integration change requires a new API number and matching Foci release. Foci still requires core 0.7.3 or newer because earlier cores do not advertise this contract. The Simple Radio version requirement remains unchanged.

Foci's release number is now independent of the core release number. Core's offered API and Foci's required API are separate build properties, so changing one cannot silently approve the other.

Radio behavior, terrain settings, saved data, recipes and rendering are unchanged. Directional Antenna remains 0.2.9 and continues to work without either addon.
