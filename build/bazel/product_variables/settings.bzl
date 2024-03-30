"""Macros to generate constraint settings and values for Soong variables."""

def soong_config_variables(bool_vars, value_vars, string_vars):
    for variable in bool_vars.keys() + value_vars.keys():
        variable = variable.lower()
        native.constraint_setting(
            name = variable + "_constraint",
        )
        native.constraint_value(
            name = variable,
            constraint_setting = variable + "_constraint",
        )
    for variable, choices in string_vars.items():
        for choice in choices:
            var_with_choice = (variable + "__" + choice).lower()
            native.constraint_setting(
                name = var_with_choice + "_constraint",
            )
            native.constraint_value(
                name = var_with_choice,
                constraint_setting = var_with_choice + "_constraint",
            )
