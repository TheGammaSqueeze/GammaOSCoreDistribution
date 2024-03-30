"""
Copyright (C) 2021 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

ApexKeyInfo = provider(
    "Info needed to sign APEX bundles",
    fields = {
        "public_key": "File containing the public_key",
        "private_key": "File containing the private key",
    },
)

def _apex_key_rule_impl(ctx):
    return [
        ApexKeyInfo(public_key = ctx.file.public_key, private_key = ctx.file.private_key),
    ]

apex_key = rule(
    implementation = _apex_key_rule_impl,
    attrs = {
        "public_key": attr.label(mandatory = True, allow_single_file = True),
        "private_key": attr.label(mandatory = True, allow_single_file = True),
    },
)
