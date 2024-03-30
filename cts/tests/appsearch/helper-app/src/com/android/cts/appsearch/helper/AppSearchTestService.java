/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.cts.appsearch.helper;

import static android.app.appsearch.testutil.AppSearchTestUtils.checkIsBatchResultSuccess;
import static android.app.appsearch.testutil.AppSearchTestUtils.convertSearchResultsToDocuments;

import android.app.Service;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSchema;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.GetSchemaResponse;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchEmail;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.ArraySet;
import android.util.Log;

import com.android.cts.appsearch.ICommandReceiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class AppSearchTestService extends Service {

    private static final String TAG = "AppSearchTestService";
    private GlobalSearchSessionShim mGlobalSearchSessionShim;

    @Override
    public void onCreate() {
        try {
            // We call this here so we can pass in a context. If we try to create the session in the
            // stub, it'll try to grab the context from ApplicationProvider. But that will fail
            // since this isn't instrumented.
            mGlobalSearchSessionShim =
                    GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(this).get();

        } catch (Exception e) {
            Log.e(TAG, "Error starting service.", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new CommandReceiver();
    }

    private class CommandReceiver extends ICommandReceiver.Stub {

        @Override
        public List<String> globalSearch(String queryExpression) {
            try {
                final SearchSpec searchSpec =
                        new SearchSpec.Builder()
                                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                .build();
                SearchResultsShim searchResults =
                        mGlobalSearchSessionShim.search(queryExpression, searchSpec);
                List<GenericDocument> results = convertSearchResultsToDocuments(searchResults);

                List<String> resultStrings = new ArrayList<>();
                for (GenericDocument doc : results) {
                    resultStrings.add(doc.toString());
                }

                return resultStrings;
            } catch (Exception e) {
                Log.e(TAG, "Error issuing global search.", e);
                return Collections.emptyList();
            }
        }

        @Override
        public List<String> globalGet(
                String packageName, String databaseName, String namespace, String id) {
            try {
                AppSearchBatchResult<String, GenericDocument> getResult =
                        mGlobalSearchSessionShim.getByDocumentIdAsync(
                                packageName,
                                databaseName,
                                new GetByDocumentIdRequest.Builder(namespace)
                                        .addIds(id)
                                        .build())
                                .get();

                List<String> resultStrings = new ArrayList<>();
                for (String docKey : getResult.getSuccesses().keySet()) {
                    resultStrings.add(getResult.getSuccesses().get(docKey).toString());
                }

                return resultStrings;
            } catch (Exception e) {
                Log.e(TAG, "Error issuing global get.", e);
                return Collections.emptyList();
            }
        }

        public List<String> globalGetSchema(String packageName, String databaseName) {
            try {
                GetSchemaResponse response =
                        mGlobalSearchSessionShim.getSchema(packageName, databaseName).get();
                if (response == null || response.getSchemas().isEmpty()) {
                    return null;
                }
                List<String> schemas = new ArrayList(response.getSchemas().size());
                for (AppSearchSchema schema : response.getSchemas()) {
                    schemas.add(schema.toString());
                }
                return schemas;
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving global schema.", e);
                return null;
            }
        }

        @Override
        public boolean indexGloballySearchableDocument(
                String databaseName, String namespace, String id, List<Bundle> permissionBundles) {
            try {
                AppSearchSessionShim db =
                        AppSearchSessionShimImpl.createSearchSessionAsync(
                                AppSearchTestService.this,
                                new AppSearchManager.SearchContext.Builder(databaseName).build(),
                                Executors.newCachedThreadPool())
                                .get();

                // By default, schemas/documents are globally searchable. We don't purposely set
                // setSchemaTypeDisplayedBySystem(false) for this schema
                SetSchemaRequest.Builder setSchemaRequestBuilder =
                        new SetSchemaRequest.Builder()
                                .setForceOverride(true)
                                .addSchemas(AppSearchEmail.SCHEMA);
                for (int i = 0; i < permissionBundles.size(); i++) {
                    setSchemaRequestBuilder.addRequiredPermissionsForSchemaTypeVisibility(
                            AppSearchEmail.SCHEMA_TYPE,
                            new ArraySet<>(permissionBundles.get(i)
                                    .getIntegerArrayList("permission")));
                }
                db.setSchema(setSchemaRequestBuilder.build()).get();

                AppSearchEmail emailDocument =
                        new AppSearchEmail.Builder(namespace, id)
                                .setFrom("from@example.com")
                                .setTo("to1@example.com", "to2@example.com")
                                .setSubject("subject")
                                .setBody("this is the body of the email")
                                .build();
                checkIsBatchResultSuccess(
                        db.put(
                                new PutDocumentsRequest.Builder()
                                        .addGenericDocuments(emailDocument)
                                        .build()));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to index globally searchable document.", e);
            }
            return false;
        }

        @Override
        public boolean indexNotGloballySearchableDocument(
                String databaseName, String namespace, String id) {
            try {
                AppSearchSessionShim db =
                        AppSearchSessionShimImpl.createSearchSessionAsync(
                                AppSearchTestService.this,
                                new AppSearchManager.SearchContext.Builder(databaseName).build(),
                                Executors.newCachedThreadPool())
                                .get();

                db.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setForceOverride(true)
                                .setSchemaTypeDisplayedBySystem(
                                        AppSearchEmail.SCHEMA_TYPE, /*displayed=*/ false)
                                .build())
                        .get();

                AppSearchEmail emailDocument =
                        new AppSearchEmail.Builder(namespace, id)
                                .setFrom("from@example.com")
                                .setTo("to1@example.com", "to2@example.com")
                                .setSubject("subject")
                                .setBody("this is the body of the email")
                                .build();
                checkIsBatchResultSuccess(
                        db.put(
                                new PutDocumentsRequest.Builder()
                                        .addGenericDocuments(emailDocument)
                                        .build()));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to index not-globally searchable document.", e);
            }
            return false;
        }

        public boolean clearData(String databaseName) {
            try {
                // Force override with empty schema will clear all previous schemas and their
                // documents.
                AppSearchSessionShim db =
                        AppSearchSessionShimImpl.createSearchSessionAsync(
                                AppSearchTestService.this,
                                new AppSearchManager.SearchContext.Builder(databaseName).build(),
                                Executors.newCachedThreadPool())
                                .get();

                db.setSchema(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();

                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear data.", e);
            }
            return false;
        }
    }
}
