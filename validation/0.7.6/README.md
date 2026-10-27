# 0.7.6 compass correction and suite review

The behavioral change is limited to Relay Monitor compass estimates. TX-only binding uses the visible 16-point sector and rounded RF distance, converted into physical destination blocks without copying destination height. Heard nodes remain exact. Surveys retain their existing measurement/status behavior; configuration and block hooks are unchanged.

109 unit tests, 93 core server tests and 139 combined server tests passed. Existing monitor item, tracking, stack, offhand and passive-module checks remain. New coverage includes same-visible-value equivalence, fixed estimates followed by precise RX rebinding, local Nether scaling and both Foci projection directions. No test or host-agent classes ship. The suite review is in docs/Simple-Radio-Suite-Review-0.7.5.md; its source findings refer to the unmodified 0.7.5 baseline.
