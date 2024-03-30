# Bazel rules for Android Platform.

This directory contains Starlark extensions for building the Android Platform with Bazel.

## APEX

Run the following command to build a miminal APEX example.

```
$ b build //build/bazel/examples/apex/minimal:build.bazel.examples.apex.minimal
```

Verify the contents of the APEX with `zipinfo`:

```
$ zipinfo bazel-bin/build/bazel/examples/apex/minimal/build.bazel.examples.apex.minimal.apex
```
