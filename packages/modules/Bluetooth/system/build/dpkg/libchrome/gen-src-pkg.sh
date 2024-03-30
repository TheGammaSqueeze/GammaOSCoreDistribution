#!/bin/bash
# Generates Debian source and binary packages of libchrome.

if [ -z "$1" ]; then
        echo "Usage: gen-src-pkg.sh <output-dir>"
        exit 1
fi

outdir="$1"
pkgdir=libchrome-930012
origtar=libchrome_930012.orig.tar.gz
scriptdir="$( cd "$( dirname "$0" )" && pwd )"

# Pin the libchrome branch + commit
libchrome_branch=master
libchrome_commit=4b86c42f09b7c8d88b0233c60f59bafeb4d8df19

# Pin the platform2 branch + commit
platform2_branch=main
platform2_commit=4567e833015453b3ea322eec1201cc41ecdfdec0

tmpdir=$(mktemp -d)
echo Generating source package in "${tmpdir}".

# Download platform2 source.
cd "${tmpdir}"
git clone --branch "${platform2_branch}" https://chromium.googlesource.com/chromiumos/platform2 || exit 1
(cd platform2 && git checkout "${platform2_commit}")
mkdir "${pkgdir}"
cd "${pkgdir}"
# Trim platform2, only common-mk is needed.
cp -a ../platform2/{common-mk,.gn} .

# Download libchrome source and apply Chrome OS's patches.
git clone --branch "${libchrome_branch}" https://chromium.googlesource.com/aosp/platform/external/libchrome || exit 1
cd libchrome
git checkout "${libchrome_commit}"
rm -rf .git
while read -r patch; do
  patch -p1 < "libchrome_tools/patches/${patch}"
done < <(grep -E '^[^#]' "libchrome_tools/patches/patches")

# Clean up temporary platform2 checkout.
cd ../..
rm -rf platform2

# Debian requires creating .orig.tar.gz.
tar czf "${origtar}" "${pkgdir}"

# Debianize the source.
cd "${pkgdir}"
yes | debmake || exit 1
cp -aT "${scriptdir}/debian/" "${tmpdir}/${pkgdir}/debian/"

# If building for docker, use the right install script.
if [ ! -z "${LIBCHROME_DOCKER}" ]; then
  mv "${tmpdir}/${pkgdir}/debian/libchrome.install.docker" \
     "${tmpdir}/${pkgdir}/debian/libchrome.install"
else
  rm -f "${tmpdir}/${pkgdir}/debian/libchrome.install.docker"
fi

# Build source package and binary package.
cd "${tmpdir}/${pkgdir}"
dpkg-buildpackage --no-sign || exit 1

# Copy the results to output dir.
cd "${tmpdir}"
mkdir -p "${outdir}/src"
cp *.dsc *.orig.tar.gz *.debian.tar.xz "${outdir}/src"
cp *.deb "${outdir}"
cd /

echo Removing temporary directory "${tmpdir}".
rm -rf "${tmpdir}"

echo Done. Check out Debian source package in "${outdir}".
