"""Constants for product variables based on information in variable.go"""

load(
    "@soong_injection//product_config:soong_config_variables.bzl",
    _soong_config_bool_variables = "soong_config_bool_variables",
    _soong_config_string_variables = "soong_config_string_variables",
    _soong_config_value_variables = "soong_config_value_variables",
)
load(
    "@soong_injection//product_config:product_variables.bzl",
    _arch_variant_product_var_constraints = "arch_variant_product_var_constraints",
    _product_var_constraints = "product_var_constraints",
)

_soong_config_variables = _soong_config_bool_variables.keys() + \
                          _soong_config_string_variables.keys() + \
                          _soong_config_value_variables.keys()

_product_variables = {
    var: True
    for var in _product_var_constraints
}

_arch_variant_product_variables = {
    var: True
    for var in _arch_variant_product_var_constraints
}

_arch_variant_to_constraints = {
    "arm": "//build/bazel/platforms/arch:arm",
    "arm64": "//build/bazel/platforms/arch:arm64",
    "x86": "//build/bazel/platforms/arch:x86",
    "x86_64": "//build/bazel/platforms/arch:x86_64",
    "android": "//build/bazel/platforms/os:android",
    "darwin": "//build/bazel/platforms/os:darwin",
    "linux": "//build/bazel/platforms/os:linux",
    "linux_bionic": "//build/bazel/platforms/os:linux_bionic",
    "windows": "//build/bazel/platforms/os:windows",
}

constants = struct(
    SoongConfigVariables = _soong_config_variables,
    SoongConfigBoolVariables = _soong_config_bool_variables,
    SoongConfigStringVariables = _soong_config_string_variables,
    SoongConfigValueVariables = _soong_config_value_variables,
    ProductVariables = _product_variables,
    ArchVariantProductVariables = _arch_variant_product_variables,
    ArchVariantToConstraints = _arch_variant_to_constraints,
)
