# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# We have to use PRODUCT_PACKAGES (together with BUILD_HYPH) instead of
# PRODUCT_COPY_FILES to install the pattern files, so that the NOTICE file can
# get installed too.

pattern_locales := \
    af \
    as \
    be \
    bg \
    bn \
    cs \
    cu \
    cy \
    da \
    de-1901 \
    de-1996 \
    de-ch-1901 \
    el \
    en-gb \
    en-us \
    es \
    et \
    eu \
    fr \
    ga \
    gl \
    gu \
    hi \
    hr \
    hu \
    hy \
    it \
    ka \
    kn \
    la \
    lt \
    lv \
    ml \
    mn-cyrl \
    mr \
    mul-ethi \
    nb \
    nl \
    nn \
    or \
    pa \
    pt \
    ru \
    sk \
    sl \
    sq \
    sv \
    ta \
    te \
    tk \
    uk \
    und-ethi

PRODUCT_PACKAGES := $(addprefix hyph-,$(pattern_locales)) \
    $(addsuffix .lic.txt,$(addprefix hyph-,$(pattern_locales)))

pattern_locales :=
