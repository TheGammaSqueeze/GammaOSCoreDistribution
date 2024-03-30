"""Macro wrapping the py_library rule for Soong/Bazel convergence."""

def py_library(imports = [".."], **kwargs):
    # b/208215661: Always propagate the parent directory of this target so that
    # dependent targets can use `import <modulename>` without using absolute
    # imports, which Bazel uses by default. The eventual effect of this in a
    # py_binary is that all directories contain py_library deps are added to the
    # PYTHONPATH of the py_binary stub script, enabling `import <modulename>`.
    if ".." not in imports:
        imports.append("..")

    native.py_library(
        imports = imports,
        **kwargs,
    )
