Prebuilts of APIs from platform and other Mainline modules that the ART Module
needs.

These prebuilts should reflect the APIs in Android 12 (version 31), which is the
earliest release that may take an update of the ART Module. Hence updating the
prebuilts to newer versions needs to be done with care. Prebuilts that are part
of the NDK are generally safe since they are annotated to provide a version 31
compatible ABI.

To update:

1. Submit the changes that need to go into the prebuilt.

2. Wait for new builds on branch `aosp-master`, target
   `mainline_modules_sdks-userdebug`.

3. Run:

   ```
   prebuilts/runtime/mainline/update.py -b aosp-master --build <build id>
   ```

   where `<build id>` is the ID of the build in step #2.

   The `update-py` script has code to download and unpack all prebuilts, but
   most of them are commented out (see the `PREBUILT_INSTALL_MODULES` list).
   Hence you may need to tweak the script to download the prebuilts you need.

4. Try to minimize the updates in the CL created in `prebuilts/runtime`, and
   ensure they are safe. mast@google.com is happy to help with review.
