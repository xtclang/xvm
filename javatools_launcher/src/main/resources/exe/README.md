***WARNING: DO NOT MOVE OR CHANGE THE CONTENTS HERE UNTIL YOU READ AND
UNDERSTAND THIS!***

This directory contains stable build artifacts that do not get rebuilt
as part of the automated build. These are the "native launchers" for
the Java-based toolchain and runtime; their purpose is to launch the
JVM from the command line. These launchers can be installed as the
"xec" and "xcc" commands for specific hardware/OS combinations.

While they can be rebuilt, having a pre-built copy of these is
convenient from a risk-mitigation standpoint, since these are native
executables and thus a potential target for a supply-chain attack.
Additionally, the "official" copy stored in git can be signed as an
additional safeguard.

(On UNIX/Linux platforms, these may be able to be replaced with scripts
without degrading the user experience.)