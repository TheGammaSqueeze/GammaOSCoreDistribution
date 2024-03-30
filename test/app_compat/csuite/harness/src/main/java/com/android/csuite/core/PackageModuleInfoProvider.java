/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.csuite.core;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** A module info provider that accepts package names and files that contains package names. */
public final class PackageModuleInfoProvider implements ModuleInfoProvider {
    @VisibleForTesting static final String PACKAGE_OPTION = "package";
    @VisibleForTesting static final String PACKAGE_PLACEHOLDER = "{package}";

    @Option(
            name = PACKAGE_OPTION,
            description = "App package names.",
            importance = Importance.NEVER)
    private final Set<String> mPackages = new HashSet<>();

    @Override
    public Stream<ModuleInfoProvider.ModuleInfo> get(IConfiguration configuration)
            throws IOException {
        ModuleTemplate moduleTemplate = ModuleTemplate.loadFrom(configuration);

        return mPackages.stream()
                .distinct()
                .map(
                        packageName ->
                                new ModuleInfoProvider.ModuleInfo(
                                        packageName,
                                        moduleTemplate.substitute(
                                                packageName,
                                                Map.of(PACKAGE_PLACEHOLDER, packageName))));
    }
}
