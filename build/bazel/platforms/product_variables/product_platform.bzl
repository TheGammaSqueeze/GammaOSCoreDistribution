"""Parallels variable.go to provide variables and create a platform based on converted config."""

load("//build/bazel/product_variables:constants.bzl", "constants")
load("//prebuilts/clang/host/linux-x86:cc_toolchain_constants.bzl", "variant_name")

def _product_variables_providing_rule_impl(ctx):
    return [
        platform_common.TemplateVariableInfo(ctx.attr.product_vars),
    ]

# Provides product variables for templated string replacement.
product_variables_providing_rule = rule(
    implementation = _product_variables_providing_rule_impl,
    attrs = {
        "product_vars": attr.string_dict(),
    },
)

_arch_os_only_suffix = "_arch_os"
_product_only_suffix = "_product"

def add_providing_var(providing_vars, typ, var, value):
    if typ == "bool":
        providing_vars[var] = "1" if value else "0"
    elif typ == "list":
        providing_vars[var] = ",".join(value)
    elif typ == "int":
        providing_vars[var] = str(value)
    elif typ == "string":
        providing_vars[var] = value

def product_variable_config(name, product_config_vars):
    constraints = []

    local_vars = dict(product_config_vars)

    # Native_coverage is not set within soong.variables, but is hardcoded
    # within config.go NewConfig
    local_vars["Native_coverage"] = (
        local_vars.get("ClangCoverage", False) or
        local_vars.get("GcovCoverage", False)
    )

    providing_vars = {}

    # Generate constraints for Soong config variables (bool, value, string typed).
    vendor_vars = local_vars.pop("VendorVars", default = {})
    for (namespace, variables) in vendor_vars.items():
        for (var, value) in variables.items():
            # All vendor vars are Starlark string-typed, even though they may be
            # boxed bools/strings/arbitrary printf'd values, like numbers, so
            # we'll need to do some translation work here by referring to
            # soong_injection's generated data.

            if value == "":
                # Variable is not set so skip adding this as a constraint.
                continue

            # Create the identifier for the constraint var (or select key)
            config_var = namespace + "__" + var

            # List of all soong_config_module_type variables.
            if not config_var in constants.SoongConfigVariables:
                continue

            # Normalize all constraint vars (i.e. select keys) to be lowercased.
            constraint_var = config_var.lower()

            if config_var in constants.SoongConfigBoolVariables:
                constraints.append("//build/bazel/product_variables:" + constraint_var)
            elif config_var in constants.SoongConfigStringVariables:
                # The string value is part of the the select key.
                constraints.append("//build/bazel/product_variables:" + constraint_var + "__" + value.lower())
            elif config_var in constants.SoongConfigValueVariables:
                # For value variables, providing_vars add support for substituting
                # the value using TemplateVariableInfo.
                constraints.append("//build/bazel/product_variables:" + constraint_var)
                add_providing_var(providing_vars, "string", constraint_var, value)

    for (var, value) in local_vars.items():
        # TODO(b/187323817): determine how to handle remaining product
        # variables not used in product_variables
        constraint_var = var.lower()
        if not constants.ProductVariables.get(constraint_var):
            continue

        # variable.go excludes nil values
        add_constraint = (value != None)
        add_providing_var(providing_vars, type(value), var, value)
        if type(value) == "bool":
            # variable.go special cases bools
            add_constraint = value

        if add_constraint:
            constraints.append("//build/bazel/product_variables:" + constraint_var)

    native.platform(
        name = name + _product_only_suffix,
        constraint_values = constraints,
    )

    arch = local_vars.get("DeviceArch")
    arch_variant = local_vars.get("DeviceArchVariant")
    cpu_variant = local_vars.get("DeviceCpuVariant")

    os = "android"

    native.alias(
        name = name,
        actual = "{os}_{arch}{variant}".format(os = os, arch = arch, variant = _variant_name(arch, arch_variant, cpu_variant)),
    )

    arch = local_vars.get("DeviceSecondaryArch")
    arch_variant = local_vars.get("DeviceSecondaryArchVariant")
    cpu_variant = local_vars.get("DeviceSecondaryCpuVariant")

    if arch:
        native.alias(
            name = name + "_secondary",
            actual = "{os}_{arch}{variant}".format(os = os, arch = arch, variant = _variant_name(arch, arch_variant, cpu_variant)),
        )

    product_variables_providing_rule(
        name = name + "_product_vars",
        product_vars = providing_vars,
    )

def _is_variant_default(arch, variant):
    return variant == None or variant in (arch, "generic")

def _variant_name(arch, arch_variant, cpu_variant):
    if _is_variant_default(arch, arch_variant):
        arch_variant = ""
    if _is_variant_default(arch, cpu_variant):
        cpu_variant = ""
    variant = struct(
        arch_variant = arch_variant,
        cpu_variant = cpu_variant,
    )
    return variant_name(variant)

def android_platform(name = None, constraint_values = [], product = None):
    """ android_platform creates a platform with the specified constraint_values and product constraints."""
    native.platform(
        name = name,
        constraint_values = constraint_values,
        parents = [product + _product_only_suffix],
    )
