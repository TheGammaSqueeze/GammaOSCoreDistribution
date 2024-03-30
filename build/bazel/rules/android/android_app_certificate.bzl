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

AndroidAppCertificateInfo = provider(
    "Info needed for Android app certificates",
    fields = {
        "pem": "Certificate .pem file",
        "pk8": "Certificate .pk8 file",
    },
)

def _android_app_certificate_rule_impl(ctx):
    return [
        AndroidAppCertificateInfo(pem = ctx.file.pem, pk8 = ctx.file.pk8),
    ]

_android_app_certificate = rule(
    implementation = _android_app_certificate_rule_impl,
    attrs = {
        "pem": attr.label(mandatory = True, allow_single_file = [".pem"]),
        "pk8": attr.label(mandatory = True, allow_single_file = [".pk8"]),
    },
)

def android_app_certificate(
        name,
        certificate,
        **kwargs):
    "Bazel macro to correspond with the Android app certificate Soong module."

    _android_app_certificate(
        name = name,
        pem = certificate + ".x509.pem",
        pk8 = certificate + ".pk8",
        **kwargs
    )
