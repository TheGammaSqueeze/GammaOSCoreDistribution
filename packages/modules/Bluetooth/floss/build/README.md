# Podman build for Floss

This repo contains the Podman image build rule, used to generate the Podman
image necessary to build Floss. If building a new Podman image, run
`podman-build-image.py` with the tag `floss:latest`.

## Using the podman image to build

Once the Podman image is built (and assuming it's tagged as `floss:latest`), you
should use the `build-in-podman.py` script to build the current repo.

This script will use the local `floss:latest` (or pull it from the registry),
mount (or create) the `floss-out` volume to `/root/.floss` and the current
source to `/root/src` before running these commands in the container:

* `cd /root/src`
* `./build.py --run-bootstrap`
* `./build.py --libdir=/usr/lib/x86-64_linux_gnu/`

If you want to run the build more quickly (or pass other commands), run
`build-in-podman.py --only-start`. This will only start the container for you
(doing the correct mounts) and will print the commands it would have run via
`podman exec` normally.
