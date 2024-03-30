#!/usr/bin/env python3
#  Copyright (C) 2021 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# This script help add the missing "IA" APN type to the APN entry that support "default" APN type
import re
with open('apns-full-conf.xml', 'r') as ifile, open('new-apns-full-conf.xml', 'w') as ofile:
    RE_TYPE = re.compile(r"^\s*type")
    RE_IA_DEFAULT = re.compile(r"(?!.*ia)default")
    for line in ifile:
        if re.match(RE_TYPE, line):
            ofile.write(re.sub(RE_IA_DEFAULT, "default,ia", line))
        else:
            ofile.write(line)



