# Pandora Bluetooth test interfaces

Pandora Bluetooth test interfaces are Remote Procedure Call (RPC) interfaces
exposed to testing tools to trigger behaviors within a Bluetooth stack under
test. They are built using the following [requirements](doc/overview.md) and
[style guide](doc/style-guide.md).

A test interface is defined for each Bluetooth profile.

## Supported profiles

* **Host**: interfaces for general methods (pairing, connection...).
* **A2DP**: Advanced Audio Distribution Profile.
