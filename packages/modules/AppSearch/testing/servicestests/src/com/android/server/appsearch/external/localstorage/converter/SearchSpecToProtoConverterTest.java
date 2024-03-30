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

package com.android.server.appsearch.external.localstorage.converter;

import static android.app.appsearch.SearchSpec.GROUPING_TYPE_PER_PACKAGE;
import static android.app.appsearch.SearchSpec.ORDER_ASCENDING;
import static android.app.appsearch.SearchSpec.RANKING_STRATEGY_CREATION_TIMESTAMP;

import static com.android.server.appsearch.external.localstorage.util.PrefixUtil.createPrefix;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.SearchSpec;
import android.app.appsearch.testutil.AppSearchTestUtils;

import com.android.server.appsearch.external.localstorage.AppSearchImpl;
import com.android.server.appsearch.external.localstorage.OptimizeStrategy;
import com.android.server.appsearch.external.localstorage.UnlimitedLimitConfig;
import com.android.server.appsearch.external.localstorage.util.PrefixUtil;
import com.android.server.appsearch.external.localstorage.visibilitystore.CallerAccess;
import com.android.server.appsearch.external.localstorage.visibilitystore.VisibilityStore;
import com.android.server.appsearch.icing.proto.ResultSpecProto;
import com.android.server.appsearch.icing.proto.SchemaTypeConfigProto;
import com.android.server.appsearch.icing.proto.ScoringSpecProto;
import com.android.server.appsearch.icing.proto.SearchSpecProto;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Map;
import java.util.Set;

public class SearchSpecToProtoConverterTest {
    /** An optimize strategy that always triggers optimize. */
    public static final OptimizeStrategy ALWAYS_OPTIMIZE = optimizeInfo -> true;

    @Rule public final TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testToSearchSpecProto() throws Exception {
        SearchSpec searchSpec = new SearchSpec.Builder().build();
        String prefix1 = PrefixUtil.createPrefix("package", "database1");
        String prefix2 = PrefixUtil.createPrefix("package", "database2");

        SchemaTypeConfigProto configProto = SchemaTypeConfigProto.getDefaultInstance();
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1, prefix2),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1,
                                        ImmutableSet.of(
                                                prefix1 + "namespace1", prefix1 + "namespace2"),
                                prefix2,
                                        ImmutableSet.of(
                                                prefix2 + "namespace1", prefix2 + "namespace2")),
                        /*schemaMap=*/ ImmutableMap.of(
                                prefix1,
                                        ImmutableMap.of(
                                                prefix1 + "typeA", configProto,
                                                prefix1 + "typeB", configProto),
                                prefix2,
                                        ImmutableMap.of(
                                                prefix2 + "typeA", configProto,
                                                prefix2 + "typeB", configProto)));
        // Convert SearchSpec to proto.
        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "query");

        assertThat(searchSpecProto.getQuery()).isEqualTo("query");
        assertThat(searchSpecProto.getSchemaTypeFiltersList())
                .containsExactly(
                        "package$database1/typeA",
                        "package$database1/typeB",
                        "package$database2/typeA",
                        "package$database2/typeB");
        assertThat(searchSpecProto.getNamespaceFiltersList())
                .containsExactly(
                        "package$database1/namespace1", "package$database1/namespace2",
                        "package$database2/namespace1", "package$database2/namespace2");
    }

    @Test
    public void testToScoringSpecProto() {
        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setOrder(ORDER_ASCENDING)
                        .setRankingStrategy(RANKING_STRATEGY_CREATION_TIMESTAMP)
                        .build();

        ScoringSpecProto scoringSpecProto =
                new SearchSpecToProtoConverter(
                                searchSpec,
                                /*prefixes=*/ ImmutableSet.of(),
                                /*namespaceMap=*/ ImmutableMap.of(),
                                /*schemaMap=*/ ImmutableMap.of())
                        .toScoringSpecProto();

        assertThat(scoringSpecProto.getOrderBy().getNumber())
                .isEqualTo(ScoringSpecProto.Order.Code.ASC_VALUE);
        assertThat(scoringSpecProto.getRankBy().getNumber())
                .isEqualTo(ScoringSpecProto.RankingStrategy.Code.CREATION_TIMESTAMP_VALUE);
    }

    @Test
    public void testToResultSpecProto() {
        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setResultCountPerPage(123)
                        .setSnippetCount(234)
                        .setSnippetCountPerProperty(345)
                        .setMaxSnippetSize(456)
                        .build();

        SearchSpecToProtoConverter convert =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(),
                        /*namespaceMap=*/ ImmutableMap.of(),
                        /*schemaMap=*/ ImmutableMap.of());
        ResultSpecProto resultSpecProto =
                convert.toResultSpecProto(/*namespaceMap=*/ ImmutableMap.of());

        assertThat(resultSpecProto.getNumPerPage()).isEqualTo(123);
        assertThat(resultSpecProto.getSnippetSpec().getNumToSnippet()).isEqualTo(234);
        assertThat(resultSpecProto.getSnippetSpec().getNumMatchesPerProperty()).isEqualTo(345);
        assertThat(resultSpecProto.getSnippetSpec().getMaxWindowUtf32Length()).isEqualTo(456);
    }

    @Test
    public void testToResultSpecProto_groupByPackage() {
        SearchSpec searchSpec =
                new SearchSpec.Builder().setResultGrouping(GROUPING_TYPE_PER_PACKAGE, 5).build();

        String prefix1 = PrefixUtil.createPrefix("package1", "database");
        String prefix2 = PrefixUtil.createPrefix("package2", "database");

        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1, prefix2),
                        /*namespaceMap=*/ ImmutableMap.of(),
                        /*schemaMap=*/ ImmutableMap.of());
        ResultSpecProto resultSpecProto =
                converter.toResultSpecProto(
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1,
                                        ImmutableSet.of(
                                                prefix1 + "namespaceA", prefix1 + "namespaceB"),
                                prefix2,
                                        ImmutableSet.of(
                                                prefix2 + "namespaceA", prefix2 + "namespaceB")));

        assertThat(resultSpecProto.getResultGroupingsCount()).isEqualTo(2);
        // First grouping should have same package name.
        ResultSpecProto.ResultGrouping grouping1 = resultSpecProto.getResultGroupings(0);
        assertThat(grouping1.getMaxResults()).isEqualTo(5);
        assertThat(grouping1.getNamespacesCount()).isEqualTo(2);
        assertThat(PrefixUtil.getPackageName(grouping1.getNamespaces(0)))
                .isEqualTo(PrefixUtil.getPackageName(grouping1.getNamespaces(1)));

        // Second grouping should have same package name.
        ResultSpecProto.ResultGrouping grouping2 = resultSpecProto.getResultGroupings(1);
        assertThat(grouping2.getMaxResults()).isEqualTo(5);
        assertThat(grouping2.getNamespacesCount()).isEqualTo(2);
        assertThat(PrefixUtil.getPackageName(grouping2.getNamespaces(0)))
                .isEqualTo(PrefixUtil.getPackageName(grouping2.getNamespaces(1)));
    }

    @Test
    public void testToResultSpecProto_groupByNamespace() throws Exception {
        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setResultGrouping(SearchSpec.GROUPING_TYPE_PER_NAMESPACE, 5)
                        .build();

        String prefix1 = PrefixUtil.createPrefix("package1", "database");
        String prefix2 = PrefixUtil.createPrefix("package2", "database");

        Map<String, Set<String>> namespaceMap =
                ImmutableMap.of(
                        prefix1, ImmutableSet.of(prefix1 + "namespaceA", prefix1 + "namespaceB"),
                        prefix2, ImmutableSet.of(prefix2 + "namespaceA", prefix2 + "namespaceB"));
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1, prefix2),
                        namespaceMap,
                        /*schemaMap=*/ ImmutableMap.of());
        ResultSpecProto resultSpecProto = converter.toResultSpecProto(namespaceMap);

        assertThat(resultSpecProto.getResultGroupingsCount()).isEqualTo(2);
        // First grouping should have same namespace.
        ResultSpecProto.ResultGrouping grouping1 = resultSpecProto.getResultGroupings(0);
        assertThat(grouping1.getNamespacesCount()).isEqualTo(2);
        assertThat(PrefixUtil.removePrefix(grouping1.getNamespaces(0)))
                .isEqualTo(PrefixUtil.removePrefix(grouping1.getNamespaces(1)));

        // Second grouping should have same namespace.
        ResultSpecProto.ResultGrouping grouping2 = resultSpecProto.getResultGroupings(1);
        assertThat(grouping2.getNamespacesCount()).isEqualTo(2);
        assertThat(PrefixUtil.removePrefix(grouping1.getNamespaces(0)))
                .isEqualTo(PrefixUtil.removePrefix(grouping1.getNamespaces(1)));
    }

    @Test
    public void testToResultSpecProto_groupByNamespaceAndPackage() throws Exception {
        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .setResultGrouping(
                                GROUPING_TYPE_PER_PACKAGE | SearchSpec.GROUPING_TYPE_PER_NAMESPACE,
                                5)
                        .build();

        String prefix1 = PrefixUtil.createPrefix("package1", "database");
        String prefix2 = PrefixUtil.createPrefix("package2", "database");
        Map<String, Set<String>> namespaceMap =
                /*namespaceMap=*/ ImmutableMap.of(
                        prefix1, ImmutableSet.of(prefix1 + "namespaceA", prefix1 + "namespaceB"),
                        prefix2, ImmutableSet.of(prefix2 + "namespaceA", prefix2 + "namespaceB"));

        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1, prefix2),
                        namespaceMap,
                        /*schemaMap=*/ ImmutableMap.of());
        ResultSpecProto resultSpecProto = converter.toResultSpecProto(namespaceMap);

        // All namespace should be separated.
        assertThat(resultSpecProto.getResultGroupingsCount()).isEqualTo(4);
        assertThat(resultSpecProto.getResultGroupings(0).getNamespacesCount()).isEqualTo(1);
        assertThat(resultSpecProto.getResultGroupings(1).getNamespacesCount()).isEqualTo(1);
        assertThat(resultSpecProto.getResultGroupings(2).getNamespacesCount()).isEqualTo(1);
        assertThat(resultSpecProto.getResultGroupings(3).getNamespacesCount()).isEqualTo(1);
    }

    @Test
    public void testGetTargetNamespaceFilters_emptySearchingFilter() {
        SearchSpec searchSpec = new SearchSpec.Builder().build();
        String prefix1 = PrefixUtil.createPrefix("package", "database1");
        String prefix2 = PrefixUtil.createPrefix("package", "database2");
        // search both prefixes
        Map<String, Set<String>> namespaceMap =
                ImmutableMap.of(
                        prefix1,
                                ImmutableSet.of(
                                        "package$database1/namespace1",
                                        "package$database1/namespace2"),
                        prefix2,
                                ImmutableSet.of(
                                        "package$database2/namespace3",
                                        "package$database2/namespace4"));
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1, prefix2),
                        namespaceMap,
                        /*schemaMap=*/ ImmutableMap.of());

        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");

        assertThat(searchSpecProto.getNamespaceFiltersList())
                .containsExactly(
                        "package$database1/namespace1", "package$database1/namespace2",
                        "package$database2/namespace3", "package$database2/namespace4");
    }

    @Test
    public void testGetTargetNamespaceFilters_searchPartialPrefix() {
        SearchSpec searchSpec = new SearchSpec.Builder().build();
        String prefix1 = PrefixUtil.createPrefix("package", "database1");
        String prefix2 = PrefixUtil.createPrefix("package", "database2");

        // Only search for prefix1
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1,
                                        ImmutableSet.of(
                                                "package$database1/namespace1",
                                                "package$database1/namespace2"),
                                prefix2,
                                        ImmutableSet.of(
                                                "package$database2/namespace3",
                                                "package$database2/namespace4")),
                        /*schemaMap=*/ ImmutableMap.of());

        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");
        // Only search prefix1 will return namespace 1 and 2.
        assertThat(searchSpecProto.getNamespaceFiltersList())
                .containsExactly("package$database1/namespace1", "package$database1/namespace2");
    }

    @Test
    public void testGetTargetNamespaceFilters_intersectionWithSearchingFilter() {
        // Put some searching namespaces.
        SearchSpec searchSpec =
                new SearchSpec.Builder().addFilterNamespaces("namespace1", "nonExist").build();
        String prefix1 = PrefixUtil.createPrefix("package", "database1");

        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1,
                                ImmutableSet.of(
                                        "package$database1/namespace1",
                                        "package$database1/namespace2")),
                        /*schemaMap=*/ ImmutableMap.of());
        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");
        // If the searching namespace filter is not empty, the target namespace filter will be the
        // intersection of the searching namespace filters that users want to search over and
        // those candidates which are stored in AppSearch.
        assertThat(searchSpecProto.getNamespaceFiltersList())
                .containsExactly("package$database1/namespace1");
    }

    @Test
    public void testGetTargetNamespaceFilters_intersectionWithNonExistFilter() {
        // Search in non-exist namespaces
        SearchSpec searchSpec = new SearchSpec.Builder().addFilterNamespaces("nonExist").build();
        String prefix1 = PrefixUtil.createPrefix("package", "database1");

        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1,
                                ImmutableSet.of(
                                        "package$database1/namespace1",
                                        "package$database1/namespace2")),
                        /*schemaMap=*/ ImmutableMap.of());
        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");
        // If the searching namespace filter is not empty, the target namespace filter will be the
        // intersection of the searching namespace filters that users want to search over and
        // those candidates which are stored in AppSearch.
        assertThat(searchSpecProto.getNamespaceFiltersList()).isEmpty();
    }

    @Test
    public void testGetTargetSchemaFilters_emptySearchingFilter() {
        SearchSpec searchSpec = new SearchSpec.Builder().build();
        String prefix1 = createPrefix("package", "database1");
        String prefix2 = createPrefix("package", "database2");
        SchemaTypeConfigProto schemaTypeConfigProto =
                SchemaTypeConfigProto.newBuilder().getDefaultInstanceForType();
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1, prefix2),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1, ImmutableSet.of("package$database1/namespace1")),
                        /*schemaMap=*/ ImmutableMap.of(
                                prefix1,
                                        ImmutableMap.of(
                                                "package$database1/typeA", schemaTypeConfigProto,
                                                "package$database1/typeB", schemaTypeConfigProto),
                                prefix2,
                                        ImmutableMap.of(
                                                "package$database2/typeC", schemaTypeConfigProto,
                                                "package$database2/typeD", schemaTypeConfigProto)));
        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");
        // Empty searching filter will get all types for target filter
        assertThat(searchSpecProto.getSchemaTypeFiltersList())
                .containsExactly(
                        "package$database1/typeA", "package$database1/typeB",
                        "package$database2/typeC", "package$database2/typeD");
    }

    @Test
    public void testGetTargetSchemaFilters_searchPartialFilter() {
        SearchSpec searchSpec = new SearchSpec.Builder().build();
        String prefix1 = createPrefix("package", "database1");
        String prefix2 = createPrefix("package", "database2");
        SchemaTypeConfigProto schemaTypeConfigProto =
                SchemaTypeConfigProto.newBuilder().getDefaultInstanceForType();
        // only search in prefix1
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1, ImmutableSet.of("package$database1/namespace1")),
                        /*schemaMap=*/ ImmutableMap.of(
                                prefix1,
                                        ImmutableMap.of(
                                                "package$database1/typeA", schemaTypeConfigProto,
                                                "package$database1/typeB", schemaTypeConfigProto),
                                prefix2,
                                        ImmutableMap.of(
                                                "package$database2/typeC", schemaTypeConfigProto,
                                                "package$database2/typeD", schemaTypeConfigProto)));
        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");
        // Only search prefix1 will return typeA and B.
        assertThat(searchSpecProto.getSchemaTypeFiltersList())
                .containsExactly("package$database1/typeA", "package$database1/typeB");
    }

    @Test
    public void testGetTargetSchemaFilters_intersectionWithSearchingFilter() {
        // Put some searching schemas.
        SearchSpec searchSpec =
                new SearchSpec.Builder().addFilterSchemas("typeA", "nonExist").build();
        String prefix1 = createPrefix("package", "database1");
        SchemaTypeConfigProto schemaTypeConfigProto =
                SchemaTypeConfigProto.newBuilder().getDefaultInstanceForType();
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1, ImmutableSet.of("package$database1/namespace1")),
                        /*schemaMap=*/ ImmutableMap.of(
                                prefix1,
                                ImmutableMap.of(
                                        "package$database1/typeA", schemaTypeConfigProto,
                                        "package$database1/typeB", schemaTypeConfigProto)));
        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");
        // If the searching schema filter is not empty, the target schema filter will be the
        // intersection of the schema filters that users want to search over and those candidates
        // which are stored in AppSearch.
        assertThat(searchSpecProto.getSchemaTypeFiltersList())
                .containsExactly("package$database1/typeA");
    }

    @Test
    public void testGetTargetSchemaFilters_intersectionWithNonExistFilter() {
        // Put non-exist searching schema.
        SearchSpec searchSpec = new SearchSpec.Builder().addFilterSchemas("nonExist").build();
        String prefix1 = createPrefix("package", "database1");
        SchemaTypeConfigProto schemaTypeConfigProto =
                SchemaTypeConfigProto.newBuilder().getDefaultInstanceForType();
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix1),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix1, ImmutableSet.of("package$database1/namespace1")),
                        /*schemaMap=*/ ImmutableMap.of(
                                prefix1,
                                ImmutableMap.of(
                                        "package$database1/typeA", schemaTypeConfigProto,
                                        "package$database1/typeB", schemaTypeConfigProto)));
        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");

        // If there is no intersection of the schema filters that user want to search over and
        // those filters which are stored in AppSearch, return empty.
        assertThat(searchSpecProto.getSchemaTypeFiltersList()).isEmpty();
    }

    @Test
    public void testRemoveInaccessibleSchemaFilter() throws Exception {
        AppSearchImpl appSearchImpl =
                AppSearchImpl.create(
                        mTemporaryFolder.newFolder(),
                        new UnlimitedLimitConfig(),
                        /*initStatsBuilder=*/ null,
                        ALWAYS_OPTIMIZE,
                        /*visibilityChecker=*/ null);
        VisibilityStore visibilityStore = new VisibilityStore(appSearchImpl);

        final String prefix = PrefixUtil.createPrefix("package", "database");
        SchemaTypeConfigProto schemaTypeConfigProto =
                SchemaTypeConfigProto.newBuilder().getDefaultInstanceForType();
        SearchSpecToProtoConverter converter =
                new SearchSpecToProtoConverter(
                        new SearchSpec.Builder().build(),
                        /*prefixes=*/ ImmutableSet.of(prefix),
                        /*namespaceMap=*/ ImmutableMap.of(
                                prefix, ImmutableSet.of("package$database/namespace1")),
                        /*schemaMap=*/ ImmutableMap.of(
                                prefix,
                                ImmutableMap.of(
                                        "package$database/schema1", schemaTypeConfigProto,
                                        "package$database/schema2", schemaTypeConfigProto,
                                        "package$database/schema3", schemaTypeConfigProto)));

        converter.removeInaccessibleSchemaFilter(
                new CallerAccess(/*callingPackageName=*/ "otherPackageName"),
                visibilityStore,
                AppSearchTestUtils.createMockVisibilityChecker(
                        /*visiblePrefixedSchemas=*/ ImmutableSet.of(
                                prefix + "schema1", prefix + "schema3")));

        SearchSpecProto searchSpecProto = converter.toSearchSpecProto(/*queryExpression=*/ "");
        // schema 2 is filtered out since it is not searchable for user.
        assertThat(searchSpecProto.getSchemaTypeFiltersList())
                .containsExactly(prefix + "schema1", prefix + "schema3");
    }

    @Test
    public void testIsNothingToSearch() {
        String prefix = PrefixUtil.createPrefix("package", "database");
        SearchSpec searchSpec =
                new SearchSpec.Builder()
                        .addFilterSchemas("schema")
                        .addFilterNamespaces("namespace")
                        .build();

        // build maps
        SchemaTypeConfigProto schemaTypeConfigProto =
                SchemaTypeConfigProto.newBuilder().getDefaultInstanceForType();
        Map<String, Map<String, SchemaTypeConfigProto>> schemaMap =
                ImmutableMap.of(
                        prefix, ImmutableMap.of("package$database/schema", schemaTypeConfigProto));
        Map<String, Set<String>> namespaceMap =
                ImmutableMap.of(prefix, ImmutableSet.of("package$database/namespace"));

        SearchSpecToProtoConverter emptySchemaConverter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix),
                        /*namespaceMap=*/ namespaceMap,
                        /*schemaMap=*/ ImmutableMap.of());
        assertThat(emptySchemaConverter.isNothingToSearch()).isTrue();

        SearchSpecToProtoConverter emptyNamespaceConverter =
                new SearchSpecToProtoConverter(
                        searchSpec,
                        /*prefixes=*/ ImmutableSet.of(prefix),
                        /*namespaceMap=*/ ImmutableMap.of(),
                        schemaMap);
        assertThat(emptyNamespaceConverter.isNothingToSearch()).isTrue();

        SearchSpecToProtoConverter nonEmptyConverter =
                new SearchSpecToProtoConverter(
                        searchSpec, /*prefixes=*/ ImmutableSet.of(prefix), namespaceMap, schemaMap);
        assertThat(nonEmptyConverter.isNothingToSearch()).isFalse();

        // remove all target schema filter, and the query becomes nothing to search.
        nonEmptyConverter.removeInaccessibleSchemaFilter(
                new CallerAccess(/*callingPackageName=*/ "otherPackageName"),
                /*visibilityStore=*/ null,
                /*visibilityChecker=*/ null);
        assertThat(nonEmptyConverter.isNothingToSearch()).isTrue();
    }
}
