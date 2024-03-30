This directory contains the api txt files for finalized sdk extension versions.
Note that the extension versions are distinct from Android API Levels, and
the version numbers evolve independently from each other.

The prebuilt_apis module is responsible for figuring out which version is the
latest finalized version for each API surface.

**Version history**:

- 1: Finalized together with Android S / 31 (all modules)
- 2: Finalized in March 2022 between S and T (mediaprovider, sdkextensions)
- 3: Finalized together with Android T / 33 (all modules)
