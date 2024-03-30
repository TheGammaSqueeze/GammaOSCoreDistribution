/*
 * Copyright 2021 The Android Open Source Project
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

package android.app.appsearch.cts.app;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.annotation.NonNull;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.Migrator;
import android.app.appsearch.PackageIdentifier;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchEmail;
import android.util.ArrayMap;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class SetSchemaRequestCtsTest {
    @Test
    public void testBuildSetSchemaRequest() {
        AppSearchSchema.StringPropertyConfig prop1 =
                new AppSearchSchema.StringPropertyConfig.Builder("prop1")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build();
        AppSearchSchema schema1 = new AppSearchSchema.Builder("type1").addProperty(prop1).build();
        AppSearchSchema schema2 = new AppSearchSchema.Builder("type2").addProperty(prop1).build();
        AppSearchSchema schema3 = new AppSearchSchema.Builder("type3").addProperty(prop1).build();
        AppSearchSchema schema4 = new AppSearchSchema.Builder("type4").addProperty(prop1).build();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", new byte[] {100});

        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema1, schema2)
                        .addSchemas(Arrays.asList(schema3, schema4))
                        .setSchemaTypeDisplayedBySystem("type2", /*displayed=*/ false)
                        .setSchemaTypeVisibilityForPackage(
                                "type1", /*visible=*/ true, packageIdentifier)
                        .setForceOverride(true)
                        .setVersion(142857)
                        .build();

        assertThat(request.getSchemas()).containsExactly(schema1, schema2, schema3, schema4);
        assertThat(request.getSchemasNotDisplayedBySystem()).containsExactly("type2");

        assertThat(request.getSchemasVisibleToPackages())
                .containsExactly("type1", Collections.singleton(packageIdentifier));
        assertThat(request.getVersion()).isEqualTo(142857);
        assertThat(request.isForceOverride()).isTrue();
    }

    @Test
    public void testSetSchemaRequestTypeChanges() {
        AppSearchSchema.StringPropertyConfig requiredProp =
                new AppSearchSchema.StringPropertyConfig.Builder("prop1")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REQUIRED)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build();
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("type1").addProperty(requiredProp).build();
        AppSearchSchema schema2 =
                new AppSearchSchema.Builder("type2").addProperty(requiredProp).build();
        AppSearchSchema schema3 =
                new AppSearchSchema.Builder("type3").addProperty(requiredProp).build();

        Migrator expectedMigrator1 =
                new Migrator() {
                    @Override
                    public boolean shouldMigrate(int currentVersion, int finalVersion) {
                        return true;
                    }

                    @NonNull
                    @Override
                    public GenericDocument onUpgrade(
                            int currentVersion,
                            int finalVersion,
                            @NonNull GenericDocument document) {
                        return document;
                    }

                    @NonNull
                    @Override
                    public GenericDocument onDowngrade(
                            int currentVersion,
                            int finalVersion,
                            @NonNull GenericDocument document) {
                        return document;
                    }
                };
        Migrator expectedMigrator2 =
                new Migrator() {
                    @Override
                    public boolean shouldMigrate(int currentVersion, int finalVersion) {
                        return true;
                    }

                    @NonNull
                    @Override
                    public GenericDocument onUpgrade(
                            int currentVersion,
                            int finalVersion,
                            @NonNull GenericDocument document) {
                        return document;
                    }

                    @NonNull
                    @Override
                    public GenericDocument onDowngrade(
                            int currentVersion,
                            int finalVersion,
                            @NonNull GenericDocument document) {
                        return document;
                    }
                };
        Migrator expectedMigrator3 =
                new Migrator() {
                    @Override
                    public boolean shouldMigrate(int currentVersion, int finalVersion) {
                        return true;
                    }

                    @NonNull
                    @Override
                    public GenericDocument onUpgrade(
                            int currentVersion,
                            int finalVersion,
                            @NonNull GenericDocument document) {
                        return document;
                    }

                    @NonNull
                    @Override
                    public GenericDocument onDowngrade(
                            int currentVersion,
                            int finalVersion,
                            @NonNull GenericDocument document) {
                        return document;
                    }
                };
        Map<String, Migrator> migratorMap = new ArrayMap<>();
        migratorMap.put("type1", expectedMigrator1);
        migratorMap.put("type2", expectedMigrator2);

        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema1, schema2, schema3)
                        .setForceOverride(/*forceOverride=*/ true)
                        .setMigrators(migratorMap)
                        .setMigrator("type3", expectedMigrator3)
                        .build();

        assertThat(request.isForceOverride()).isTrue();
        Map<String, Migrator> expectedMigratorMap = new ArrayMap<>();
        expectedMigratorMap.put("type1", expectedMigrator1);
        expectedMigratorMap.put("type2", expectedMigrator2);
        expectedMigratorMap.put("type3", expectedMigrator3);
        assertThat(request.getMigrators()).containsExactlyEntriesIn(expectedMigratorMap);
    }

    @Test
    public void testInvalidSchemaReferences_fromDisplayedBySystem() {
        IllegalArgumentException expected =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new SetSchemaRequest.Builder()
                                        .setSchemaTypeDisplayedBySystem("InvalidSchema", false)
                                        .build());
        assertThat(expected).hasMessageThat().contains("referenced, but were not added");
    }

    @Test
    public void testInvalidSchemaReferences_fromPackageVisibility() {
        IllegalArgumentException expected =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new SetSchemaRequest.Builder()
                                        .setSchemaTypeVisibilityForPackage(
                                                "InvalidSchema",
                                                /*visible=*/ true,
                                                new PackageIdentifier(
                                                        "com.foo.package",
                                                        /*sha256Certificate=*/ new byte[] {}))
                                        .build());
        assertThat(expected).hasMessageThat().contains("referenced, but were not added");
    }

    @Test
    public void testSetSchemaTypeDisplayedBySystem_displayed() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        // By default, the schema is displayed.
        SetSchemaRequest request = new SetSchemaRequest.Builder().addSchemas(schema).build();
        assertThat(request.getSchemasNotDisplayedBySystem()).isEmpty();

        request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        .setSchemaTypeDisplayedBySystem("Schema", true)
                        .build();
        assertThat(request.getSchemasNotDisplayedBySystem()).isEmpty();
    }

    @Test
    public void testSetSchemaTypeDisplayedBySystem_notDisplayed() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();
        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        .setSchemaTypeDisplayedBySystem("Schema", false)
                        .build();
        assertThat(request.getSchemasNotDisplayedBySystem()).containsExactly("Schema");
    }

    @Test
    public void testSetSchemaTypeVisibleForPermissions() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        // By default, the schema is displayed.
        SetSchemaRequest request = new SetSchemaRequest.Builder().addSchemas(schema).build();
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility()).isEmpty();

        SetSchemaRequest.Builder setSchemaRequestBuilder =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Schema",
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Schema",
                                ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA));

        request = setSchemaRequestBuilder.build();

        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Schema",
                        ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                                ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)));
    }

    @Test
    public void testClearSchemaTypeVisibleForPermissions() {
        SetSchemaRequest.Builder setSchemaRequestBuilder =
                new SetSchemaRequest.Builder()
                        .addSchemas(
                                new AppSearchSchema.Builder("Schema1").build(),
                                new AppSearchSchema.Builder("Schema2").build())
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Schema1",
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Schema1",
                                ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Schema2", ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE));

        SetSchemaRequest request = setSchemaRequestBuilder.build();

        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Schema1",
                                ImmutableSet.of(
                                        ImmutableSet.of(
                                                SetSchemaRequest.READ_SMS,
                                                SetSchemaRequest.READ_CALENDAR),
                                        ImmutableSet.of(
                                                SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)),
                        "Schema2",
                                ImmutableSet.of(
                                        ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)));

        // Clear the permissions in the builder
        setSchemaRequestBuilder.clearRequiredPermissionsForSchemaTypeVisibility("Schema1");

        // New object should be updated
        assertThat(setSchemaRequestBuilder.build().getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Schema2",
                        ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)));

        // Old object should remain unchanged
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Schema1",
                                ImmutableSet.of(
                                        ImmutableSet.of(
                                                SetSchemaRequest.READ_SMS,
                                                SetSchemaRequest.READ_CALENDAR),
                                        ImmutableSet.of(
                                                SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)),
                        "Schema2",
                                ImmutableSet.of(
                                        ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)));
    }

    @Test
    public void testSchemaTypeVisibilityForPackage_visible() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        // By default, the schema is not visible.
        SetSchemaRequest request = new SetSchemaRequest.Builder().addSchemas(schema).build();
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", new byte[] {100});

        request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        .setSchemaTypeVisibilityForPackage(
                                "Schema", /*visible=*/ true, packageIdentifier)
                        .build();
        assertThat(request.getSchemasVisibleToPackages())
                .containsExactly("Schema", Collections.singleton(packageIdentifier));
    }

    @Test
    public void testSchemaTypeVisibilityForPackage_notVisible() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        .setSchemaTypeVisibilityForPackage(
                                "Schema",
                                /*visible=*/ false,
                                new PackageIdentifier(
                                        "com.package.foo", /*sha256Certificate=*/ new byte[] {}))
                        .build();
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();
    }

    @Test
    public void testSchemaTypeVisibilityForPackage_deduped() throws Exception {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", new byte[] {100});

        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        // Set it visible for "Schema"
                        .setSchemaTypeVisibilityForPackage(
                                "Schema", /*visible=*/ true, packageIdentifier)
                        // Set it visible for "Schema" again, which should be a no-op
                        .setSchemaTypeVisibilityForPackage(
                                "Schema", /*visible=*/ true, packageIdentifier)
                        .build();
        assertThat(request.getSchemasVisibleToPackages())
                .containsExactly("Schema", Collections.singleton(packageIdentifier));
    }

    @Test
    public void testSchemaTypeVisibilityForPackage_removed() throws Exception {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        // First set it as visible
                        .setSchemaTypeVisibilityForPackage(
                                "Schema",
                                /*visible=*/ true,
                                new PackageIdentifier(
                                        "com.package.foo", /*sha256Certificate=*/ new byte[] {100}))
                        // Then make it not visible
                        .setSchemaTypeVisibilityForPackage(
                                "Schema",
                                /*visible=*/ false,
                                new PackageIdentifier(
                                        "com.package.foo", /*sha256Certificate=*/ new byte[] {100}))
                        .build();

        // Nothing should be visible.
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();
    }

    @Test
    public void testSetVersion() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new SetSchemaRequest.Builder()
                                        .addSchemas(AppSearchEmail.SCHEMA)
                                        .setVersion(0)
                                        .build());
        assertThat(exception).hasMessageThat().contains("Version must be a positive number");
        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(AppSearchEmail.SCHEMA)
                        .setVersion(1)
                        .build();
        assertThat(request.getVersion()).isEqualTo(1);
    }

    @Test
    public void testSetVersion_emptyDb() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SetSchemaRequest.Builder().setVersion(135).build());
        assertThat(exception)
                .hasMessageThat()
                .contains("Cannot set version to the request if schema is empty.");
    }

    @Test
    public void testRebuild() {
        byte[] sha256cert1 = new byte[32];
        byte[] sha256cert2 = new byte[32];
        Arrays.fill(sha256cert1, (byte) 1);
        Arrays.fill(sha256cert2, (byte) 2);
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("Email", sha256cert1);
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("Email", sha256cert2);
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("Email1")
                        .addProperty(
                                new AppSearchSchema.StringPropertyConfig.Builder("subject")
                                        .setCardinality(
                                                AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                                        .setIndexingType(
                                                AppSearchSchema.StringPropertyConfig
                                                        .INDEXING_TYPE_PREFIXES)
                                        .setTokenizerType(
                                                AppSearchSchema.StringPropertyConfig
                                                        .TOKENIZER_TYPE_PLAIN)
                                        .build())
                        .build();
        AppSearchSchema schema2 =
                new AppSearchSchema.Builder("Email2")
                        .addProperty(
                                new AppSearchSchema.StringPropertyConfig.Builder("subject")
                                        .setCardinality(
                                                AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                                        .setIndexingType(
                                                AppSearchSchema.StringPropertyConfig
                                                        .INDEXING_TYPE_PREFIXES)
                                        .setTokenizerType(
                                                AppSearchSchema.StringPropertyConfig
                                                        .TOKENIZER_TYPE_PLAIN)
                                        .build())
                        .build();

        SetSchemaRequest.Builder builder =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema1)
                        .setVersion(37)
                        .setSchemaTypeDisplayedBySystem("Email1", /*displayed=*/ false)
                        .setSchemaTypeVisibilityForPackage(
                                "Email1", /*visible=*/ true, packageIdentifier1)
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Email1",
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Email1",
                                ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA));

        SetSchemaRequest original = builder.build();
        SetSchemaRequest rebuild =
                builder.addSchemas(schema2)
                        .setVersion(42)
                        .setSchemaTypeDisplayedBySystem("Email2", /*displayed=*/ false)
                        .setSchemaTypeVisibilityForPackage(
                                "Email2", /*visible=*/ true, packageIdentifier2)
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Email2",
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_CONTACTS,
                                        SetSchemaRequest.READ_EXTERNAL_STORAGE))
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Email2",
                                ImmutableSet.of(SetSchemaRequest.READ_ASSISTANT_APP_SEARCH_DATA))
                        .build();

        assertThat(original.getSchemas()).containsExactly(schema1);
        assertThat(original.getVersion()).isEqualTo(37);
        assertThat(original.getSchemasNotDisplayedBySystem()).containsExactly("Email1");
        assertThat(original.getSchemasVisibleToPackages())
                .containsExactly("Email1", ImmutableSet.of(packageIdentifier1));
        assertThat(original.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Email1",
                        ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                                ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)));

        assertThat(rebuild.getSchemas()).containsExactly(schema1, schema2);
        assertThat(rebuild.getVersion()).isEqualTo(42);
        assertThat(rebuild.getSchemasNotDisplayedBySystem()).containsExactly("Email1", "Email2");
        assertThat(rebuild.getSchemasVisibleToPackages())
                .containsExactly(
                        "Email1", ImmutableSet.of(packageIdentifier1),
                        "Email2", ImmutableSet.of(packageIdentifier2));
        assertThat(rebuild.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Email1",
                        ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                                ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)),
                        "Email2",
                        ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_CONTACTS,
                                        SetSchemaRequest.READ_EXTERNAL_STORAGE),
                                ImmutableSet.of(SetSchemaRequest.READ_ASSISTANT_APP_SEARCH_DATA)));
    }

    @Test
    public void getAndModify() {
        byte[] sha256cert1 = new byte[32];
        byte[] sha256cert2 = new byte[32];
        Arrays.fill(sha256cert1, (byte) 1);
        Arrays.fill(sha256cert2, (byte) 2);
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("Email", sha256cert1);
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("Email", sha256cert2);
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("Email1")
                        .addProperty(
                                new AppSearchSchema.StringPropertyConfig.Builder("subject")
                                        .setCardinality(
                                                AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                                        .setIndexingType(
                                                AppSearchSchema.StringPropertyConfig
                                                        .INDEXING_TYPE_PREFIXES)
                                        .setTokenizerType(
                                                AppSearchSchema.StringPropertyConfig
                                                        .TOKENIZER_TYPE_PLAIN)
                                        .build())
                        .build();

        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema1)
                        .setVersion(37)
                        .setSchemaTypeDisplayedBySystem("Email1", /*displayed=*/ false)
                        .setSchemaTypeVisibilityForPackage(
                                "Email1", /*visible=*/ true, packageIdentifier1)
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Email1",
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                        .addRequiredPermissionsForSchemaTypeVisibility(
                                "Email1",
                                ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                        .build();

        // get the visibility setting and modify the output object.
        // skip getSchemasNotDisplayedBySystem since it returns an unmodifiable object.
        request.getSchemasVisibleToPackages().put("Email2", ImmutableSet.of(packageIdentifier2));
        request.getRequiredPermissionsForSchemaTypeVisibility()
                .put("Email2", ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_CALENDAR)));

        // verify we still get the original object.
        assertThat(request.getSchemasVisibleToPackages())
                .containsExactly("Email1", ImmutableSet.of(packageIdentifier1));
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Email1",
                        ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                                ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)));
    }
}
