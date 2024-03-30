<!--
  Copyright (C) 2021 The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License
  -->

# AAOS

Source code for [Android Automotive OS](https://source.android.com/devices/automotive).

## Structure

```
car_product/           - AAOS product
car-builtin-lib/       - A helper library for CarService to access hidden
                         framework APIs
car-lib/               - Car API
car-lib-module/        - Car API module
cpp/                   - Native services
experimental/          - Experimental Car API and services
packages/              - Apps and services for cars
service/               - Car service module
service-builint        - Platform builtin component that runs CarService module
tests/                 - Tests and sample apps
tools/                 - Helper scripts
```

## C++

Native (C++) code format is required to be compatible with .clang-format file. The formatter is
already integrated to `repo` tool. To run manually, use:

```
git clang-format --style=file --extension='h,cpp,cc' HEAD~
```

Note that clang-format is *not* desirable for Android java files. Therefore
the  command line above is limited to specific extensions.
