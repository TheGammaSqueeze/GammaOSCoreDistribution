# GammaOSCoreDistribution

## Overview
This README outlines the steps to build the Android firmware for GammaOS, based on LineageOS 20. This is a Generic System Image (GSI) build process, suitable for various devices. Be aware, the repository size is approximately 300GB.

## Prerequisites
- **Computer**: Recent x86_64 system (Linux, macOS, Windows; Ubuntu 20.04 LTS recommended).
- **RAM**: Minimum 32 GB.
- **Storage**: Minimum 300 GB (SSD recommended for faster build times).
- **Java**: OpenJDK 11 (included in source download).
- **Python**: Python 3 (`python-is-python3`).

## Dependencies
Install the following build packages using your distribution’s package manager:
- **bc bison build-essential ccache curl flex g++-multilib gcc-multilib git git-lfs gnupg gperf imagemagick lib32readline-dev lib32z1-dev libelf-dev liblz4-tool libsdl1.2-dev libssl-dev libxml2 libxml2-utils lzop pngcrush rsync schedtool squashfs-tools xsltproc zip zlib1g-dev**

## Building the Firmware
1. **Clone the Repo**: Ensure you are on the appropriate branch for your device.
2. **Build Command**: In the terminal, navigate to the root of the source code and issue: **./build.sh**
3. **Follow On-Screen Instructions**: The build process will guide you through the remaining steps.

## Note
This process will only build the `system.img`. The remainder of the process, including updating vbmeta keys, super images, and any boot or vendor partition updates, will be the responsibility of the user.
I will give some general guidance on this below, but expect that you as the user understand what is required here.

---------

## Creating or modifying the vbmeta image, as well as modifying boot and vendor images using the Android_boot_image_editor. 
This tool supports parsing and re-packing of `boot.img`, `vbmeta.img`, `vendor_boot.img`, and more, supporting up to Android 13.

## Prerequisites for Android_boot_image_editor
- **JDK**: JDK11 or higher.
- **Python**: Python 3.
- **Dependencies**:
  - Linux / WSL: `sudo apt install git device-tree-compiler lz4 xz-utils zlib1g-dev openjdk-17-jdk gcc g++ python3 python-is-python3 p7zip-full android-sdk-libsparse-utils`
  - Mac: `brew install lz4 xz dtc`
  - Windows: Install openssl and device-tree compiler with `choco install openssl dtc-msys2`

## Modifying Vbmeta Image
1. **Clone the Repository**:
- git clone https://github.com/cfig/Android_boot_image_editor
- cd Android_boot_image_editor

2. **Prepare vbmeta Image**:
- If modifying an existing `vbmeta.img`, place it in the current directory.
- To create a new `vbmeta.img`, use `avbtool`. For example:
  ```
  avbtool make_vbmeta_image --output vbmeta.img --algorithm SHA256_RSA4096 --key /path/to/key_used_for_signing_or_pub_key
  ```

3. **Unpack vbmeta Image**:
- rm *.img
- cp <your_vbmeta_image> vbmeta.img
- ./gradlew unpack

4. **Modify as Needed**:
- Make necessary changes to the unpacked files.

5. **Repack the Image**:
- ./gradlew pack


6. **Flash the Updated Image to Your Device** (if applicable).

## Modifying Boot or Vendor Images
1. **Prepare Boot/Vendor Images**:
- Place `boot.img` or `vendor_boot.img` in the current directory.

2. **Unpack the Image**:
- rm *.img
- cp <your_boot/vendor_image> vendor_boot.img
- ./gradlew unpack

3. **Edit the Contents**:
- Modify the files as needed in the `build/unzip_boot` directory.

4. **Repack the Image**:
- ./gradlew pack

5. **Update vbmeta Image**:
- If the `vbmeta.img` contains a hash of `boot.img` or `vendor_boot.img`, update the `vbmeta` image along with the boot/vendor image. Follow the steps in the "Modifying Vbmeta Image" section.

6. **Flash the Updated Images to Your Device**:
- Use the appropriate flashing tools and commands for your device.
