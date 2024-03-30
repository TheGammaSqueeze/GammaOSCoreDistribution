# SdkExtensions module

SdkExtensions module is responsible for:
- deciding the extension SDK level of the device;
- providing APIs for applications to query the extension SDK level;
- determining the values for the BOOTCLASSPATH, DEX2OATBOOTCLASSPATH, and
  SYSTEMSERVERCLASSPATH environment variables.

## General information

### Structure

The module is packaged in an apex, `com.android.sdkext`, and has several
components:
- `bin/derive_classpath`: a native binary that runs early in the device boot
  process. It reads individual classpath configs files from the system and
  other modules, merges them, and defines the definition of *CLASSPATH environ
  variables.
- `bin/derive_sdk`: native binary that runs early in the device boot process and
  reads metadata of other modules, to set system properties relating to the
  extension SDK (for instance `build.version.extensions.r`).
- `javalib/framework-sdkextension.jar`: this is a jar on the bootclasspath that
  exposes APIs to applications to query the extension SDK level.

### Deriving extension SDK level
`derive_sdk` is a program that reads metadata stored in other apex modules, in
the form of binary protobuf files in subpath `etc/sdkinfo.pb` inside each
apex. The structure of this protobuf can be seen [here][sdkinfo-proto]. The
exact steps for converting a set of metadata files to actual extension versions
is likely to change over time, and should not be depended upon.

### Reading extension SDK level
The module exposes a java class [`SdkExtensions`][sdkextensions-java] in the
package `android.os.ext`. The method `getExtensionVersion(int)` can be used to
read the version of a particular sdk extension, e.g.
`getExtensionVersion(Build.VERSION_CODES.R)`.

### Deriving classpaths
`derive_classpath` service reads and merges individual config files in the
`/system/etc/classpaths/` and `/apex/*/etc/classpaths`. Each config stores
protobuf message from [`classpaths.proto`] in a proto binary format. Exact
merging algorithm that determines the order of the classpath entries is
described in [`derive_classpath.cpp`] and may change over time.

[`classpaths.proto`]: packages/modules/SdkExtensions/proto/classpaths.proto
[`derive_classpath.cpp`]: packages/modules/SdkExtensions/derive_classpath/derive_classpath.cpp
[sdkinfo-proto]: packages/modules/SdkExtensions/proto/sdk.proto
[sdkextensions-java]: framework/java/android/os/ext/SdkExtensions.java

## Developer information

### Adding a new extension
An extension is a way to group a set of modules so that they are versioned
together. We currently define a new extension for every Android SDK level
that introduces new modules. Every module shipped in previous versions are
also part of the new extension. For example, all the R modules are part of
both the R extensions and the S extensions.

The steps to define a new extension are:
- Add any new modules to the SdkModule enum in sdk.proto.
- Add the binary "current sdk version" proto to the apexes of the new modules.
- Update `derive_sdk.cpp` by:
 * mapping the modules' package names to the new enum values
 * creating a new set with the new enum values of the modules relevant for
   this extension.
 * set a new sysprop to the value of `GetSdkLevel` with the new enum set
 * add a unit test to `derive_sdk_test.cpp` verifying the new extensions work
- Make the `SdkExtensions.getExtensionVersion` API support the new extensions.
- Extend the CTS test to verify the above two behaviors.
