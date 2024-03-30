:: Copyright 2021 The Android Open Source Project
::
:: Licensed under the Apache License, Version 2.0 (the "License");
:: you may not use this file except in compliance with the License.
:: You may obtain a copy of the License at
::
::      http://www.apache.org/licenses/LICENSE-2.0
::
:: Unless required by applicable law or agreed to in writing, software
:: distributed under the License is distributed on an "AS IS" BASIS,
:: WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
:: See the License for the specific language governing permissions and
:: limitations under the License.

setlocal
set TOP=%~dp0..\..\..\..
set PYTHON_SRC=%~dp0..

:: Remove Cygwin from the PATH so we use native tools (e.g. native Git).
:: (It could leave Cygwin at the very end, but that's less of a problem.)
set PATH=%PATH:C:\cygwin64\bin;=%

IF NOT DEFINED KOKORO_BUILD_ID (set KOKORO_BUILD_ID=dev)

call %~dp0build.cmd "%PYTHON_SRC%" "%TOP%\out\python3\artifact"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

py -3 %TOP%\toolchain\ndk-kokoro\gen_manifest.py --root %TOP% ^
    -o %TOP%\out\python3\artifact\manifest-%KOKORO_BUILD_ID%.xml

exit /b %ERRORLEVEL%
