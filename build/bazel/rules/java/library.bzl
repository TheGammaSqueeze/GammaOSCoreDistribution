"""Macro wrapping the java_library for bp2build. """

def java_library(name = "", srcs = [], deps = [], javacopts = [], **kwargs):
    # Disable the error prone check of HashtableContains by default. See https://errorprone.info/bugpattern/HashtableContains
    # HashtableContains error is reported when compiling //external/bouncycastle:bouncycastle-bcpkix-unbundled
    opts = ["-Xep:HashtableContains:OFF"] + javacopts

    native.java_library(name, srcs = srcs, deps = deps, javacopts = opts, **kwargs)
