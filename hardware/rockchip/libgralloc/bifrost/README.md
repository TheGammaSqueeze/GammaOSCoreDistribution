# Mali GPUs Gralloc Module User Guide

## Build time configuration

The project is designed to support multiple Android releases and HAL interface
versions. Consequently, a configuration step is required before invoking make
to build the project. The standard workflow is to setup the AOSP build
environment as usual and run `configure` with no arguments. The script will
determine which build files to enable based on the environment.

```sh
source build/envsetup.sh
lunch <target>
vendor/arm/gralloc/configure
```

## Runtime configuration

This Gralloc implementation chooses an optimal image format based on the system
hardware capabilities. These capabilities are expressed in XML and read at
runtime. All configuration files with the .xml file extension are iterated and
read from the `/vendor/etc/gralloc` directory.

### XML file format

#### Version 0.1

Not supported by this version of Gralloc.

#### Version 0.2

See `interfaces/capabilities/capabilities_type.xsd` for the XML schema
definition.

### Changelog

**Version 0.2**

* Changed XML schema to support specifying multiple IPs in a single XML file
* The IPs no longer are described based on the filename but has been moved inside
  the XML schema.

### Description

The `<capabilities>` element is the root of the XML document. It must have a
`version` attribute with the value `0.2` and a valid `<ip_capabilities>` attribute/s.

The `<ip_capabilities>` attribute describes the a set of capabilities for an IP.
The `<ip_capabilities>` has an `ip` field which specifies the actual IP for
which the capabilities are described. The following values can be specified
for the `ip` field:

| IP        |
| :-------- |
| `GPU`     |
| `DPU`     |
| `DPU_AEU` |
| `VPU`     |
| `CAM`     |

**It is important to note that these values are case sensitive and must be in
all uppercase characters.**

The table below lists the way Gralloc will interpret the relevant usages for specific
`ip` attributes used.

| IP    | Relevant usages          | Interpretation |
| :---- | :----------------------- | :------------- |
| `GPU` | `GPU_RENDER_TARGET`      | Write          |
|       | `GPU_TEXTURE`            | Read           |
|       | `GPU_DATA_BUFFER`        | Read           |
| `DPU` | `COMPOSER_CLIENT_TARGET` | Write          |
|       | `COMPOSER_OVERLAY`       | Read           |
| `VPU` | `VIDEO_ENCODER`          | Write          |
|       | `VIDEO_DECODER`          | Read           |
| `CAM` | `CAMERA_OUTPUT`          | Write          |
|       | `CAMERA_INPUT`           | Read           |

Within the `<ip_capabilities>` element are zero or more `<feature>` elements.
These elements specify what Gralloc features/formats are supported by the IP
block. Each `<feature>` element must have a `name` attribute. Listed below are
all the accepted feature names and their descriptions. In addition to the
`name` attribute, a `permission` attribute specifies whether the feature is
available for read or write access.

**Permissions**

| Permission | Description             |
| :--------- | :---------------------- |
| `RW`       | Read and write possible |
| `RO`       | Read only               |
| `WO`       | Write only              |
| `NO`       | No capability (default) |

**Additional uncompressed formats**

| Feature                     | Description                                            |
| :-------------------------- | :----------------------------------------------------- |
| `FORMAT_R10G10B10A2`        | `RGBA_1010102` format                                  |
| `FORMAT_R16G16B16A16_FLOAT` | `RGBA_FP16` format                                     |
| `YUV_BL_8`                  | 8-bit 16x16 block-linear formats (not CPU accessible)  |
| `YUV_BL_10`                 | 10-bit 16x16 block-linear formats (not CPU accessible) |

**Arm Framebuffer Compression ('AFBC') features**

| Feature                          | Description                                              |
| :------------------------------- | :------------------------------------------------------- |
| `AFBC_16X16`                     | 16x16 'basic' block size (required for all AFBC modes)   |
| `AFBC_32X8`                      | 32x8 'wide' block size                                   |
| `AFBC_64X4`                      | 64x4 'extra-wide' block size (multi-plane)               |
| `AFBC_BLOCK_SPLIT`               | Block split feature (applicable to 16x16 and 32x8 modes) |
| `AFBC_TILED_HEADERS`             | Tiled header layout                                      |
| `AFBC_DOUBLE_BODY`               | Double body layout (useful for single buffer surfaces)   |
| `AFBC_WRITE_NON_SPARSE`          | Producer requires sparse layout                          |
| `AFBC_YUV`                       | Permits AFBC with YUV formats                            |
| `AFBC_FORMAT_R16G16B16A16_FLOAT` | Permits AFBC with `RGBA_FP16` format                     |

**Arm Fixed Rate Compression ('AFRC') features**

| Feature            | Description                    |
| :------------------| :----------------------------- |
| `AFRC_ROT_LAYOUT`  | Inline rotation capable layout |
| `AFRC_SCAN_LAYOUT` | Scanline optimized layout      |

#### Example

/vendor/etc/gralloc/capabilities.xml
```xml
<capabilities version="0.2">
  <ip_capabilities ip="GPU">
    <feature name="FORMAT_R10G10B10A2" permission="RW" />
    <feature name="FORMAT_R16G16B16A16_FLOAT" permission="RW" />
    <feature name="AFBC_16X16" permission="RW" />
    <feature name="AFBC_32X8" permission="RW" />
    <feature name="AFBC_YUV" permission="RW" />
    <feature name="AFBC_BLOCK_SPLIT" permission="RW" />
    <feature name="YUV_BL_8" permission="RO" />
  </ip_capabilities>

  <ip_capabilities ip="DPU">
    <feature name="FORMAT_R10G10B10A2" permission="RO" />
    <feature name="FORMAT_R16G16B16A16_FLOAT" permission="RO" />
    <feature name="AFBC_16X16" permission="RO" />
    <feature name="AFBC_YUV" permission="RO" />
    <feature name="YUV_BL_8" permission="RO" />
  </ip_capabilities>
</capabilities>
```

#### Notes

* CPU capabilities are fixed and not customizable.
* All features are opt-in; they are assumed to be unsupported if unspecified in
  the configuration.
* Having multiple specifications of capabilities (i.e. multiple
  `<ip_capabilities>` nodes with the same `ip` attribute) in the
  configuration files should be considered invalid usage and
  will cause undefined behavior.
