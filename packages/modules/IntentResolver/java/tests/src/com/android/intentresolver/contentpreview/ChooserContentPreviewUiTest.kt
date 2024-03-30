/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver.contentpreview

import android.content.ClipDescription
import android.content.ContentInterface
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.android.intentresolver.ImageLoader
import com.android.intentresolver.TestFeatureFlagRepository
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi.ActionFactory
import com.android.intentresolver.flags.Flags
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import com.android.intentresolver.widget.ActionRow
import com.android.intentresolver.widget.ImagePreviewView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.function.Consumer

private const val PROVIDER_NAME = "org.pkg.app"
class ChooserContentPreviewUiTest {
    private val contentResolver = mock<ContentInterface>()
    private val imageClassifier = ChooserContentPreviewUi.ImageMimeTypeClassifier { mimeType ->
        mimeType != null && ClipDescription.compareMimeTypes(mimeType, "image/*")
    }
    private val imageLoader = object : ImageLoader {
        override fun loadImage(uri: Uri, callback: Consumer<Bitmap?>) {
            callback.accept(null)
        }
        override fun prePopulate(uris: List<Uri>) = Unit
        override suspend fun invoke(uri: Uri): Bitmap? = null
    }
    private val actionFactory = object : ActionFactory {
        override fun createCopyButton() = ActionRow.Action(label = "Copy", icon = null) {}
        override fun createEditButton(): ActionRow.Action? = null
        override fun createNearbyButton(): ActionRow.Action? = null
        override fun createCustomActions(): List<ActionRow.Action> = emptyList()
        override fun getModifyShareAction(): Runnable? = null
        override fun getExcludeSharedTextAction(): Consumer<Boolean> = Consumer<Boolean> {}
    }
    private val transitionCallback = mock<ImagePreviewView.TransitionElementStatusCallback>()
    private val featureFlagRepository = TestFeatureFlagRepository(
        mapOf(
            Flags.SHARESHEET_SCROLLABLE_IMAGE_PREVIEW to true
        )
    )

    @Test
    fun test_ChooserContentPreview_non_send_intent_action_to_text_preview() {
        val targetIntent = Intent(Intent.ACTION_VIEW)
        val testSubject = ChooserContentPreviewUi(
            targetIntent,
            contentResolver,
            imageClassifier,
            imageLoader,
            actionFactory,
            transitionCallback,
            featureFlagRepository
        )
        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_TEXT)
        verify(transitionCallback, times(1)).onAllTransitionElementsReady()
    }

    @Test
    fun test_ChooserContentPreview_text_mime_type_to_text_preview() {
        val targetIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Text Extra")
        }
        val testSubject = ChooserContentPreviewUi(
            targetIntent,
            contentResolver,
            imageClassifier,
            imageLoader,
            actionFactory,
            transitionCallback,
            featureFlagRepository
        )
        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_TEXT)
        verify(transitionCallback, times(1)).onAllTransitionElementsReady()
    }

    @Test
    fun test_ChooserContentPreview_single_image_uri_to_image_preview() {
        val uri = Uri.parse("content://$PROVIDER_NAME/test.png")
        val targetIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        whenever(contentResolver.getType(uri)).thenReturn("image/png")
        val testSubject = ChooserContentPreviewUi(
            targetIntent,
            contentResolver,
            imageClassifier,
            imageLoader,
            actionFactory,
            transitionCallback,
            featureFlagRepository
        )
        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        verify(transitionCallback, never()).onAllTransitionElementsReady()
    }

    @Test
    fun test_ChooserContentPreview_single_non_image_uri_to_file_preview() {
        val uri = Uri.parse("content://$PROVIDER_NAME/test.pdf")
        val targetIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        whenever(contentResolver.getType(uri)).thenReturn("application/pdf")
        val testSubject = ChooserContentPreviewUi(
            targetIntent,
            contentResolver,
            imageClassifier,
            imageLoader,
            actionFactory,
            transitionCallback,
            featureFlagRepository
        )
        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
        verify(transitionCallback, times(1)).onAllTransitionElementsReady()
    }

    @Test
    fun test_ChooserContentPreview_multiple_image_uri_to_image_preview() {
        val uri1 = Uri.parse("content://$PROVIDER_NAME/test.png")
        val uri2 = Uri.parse("content://$PROVIDER_NAME/test.jpg")
        val targetIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putExtra(
                Intent.EXTRA_STREAM,
                ArrayList<Uri>().apply {
                    add(uri1)
                    add(uri2)
                }
            )
        }
        whenever(contentResolver.getType(uri1)).thenReturn("image/png")
        whenever(contentResolver.getType(uri2)).thenReturn("image/jpeg")
        val testSubject = ChooserContentPreviewUi(
            targetIntent,
            contentResolver,
            imageClassifier,
            imageLoader,
            actionFactory,
            transitionCallback,
            featureFlagRepository
        )
        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        verify(transitionCallback, never()).onAllTransitionElementsReady()
    }

    @Test
    fun test_ChooserContentPreview_some_non_image_uri_to_file_preview() {
        val uri1 = Uri.parse("content://$PROVIDER_NAME/test.png")
        val uri2 = Uri.parse("content://$PROVIDER_NAME/test.pdf")
        val targetIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putExtra(
                Intent.EXTRA_STREAM,
                ArrayList<Uri>().apply {
                    add(uri1)
                    add(uri2)
                }
            )
        }
        whenever(contentResolver.getType(uri1)).thenReturn("image/png")
        whenever(contentResolver.getType(uri2)).thenReturn("application/pdf")
        val testSubject = ChooserContentPreviewUi(
            targetIntent,
            contentResolver,
            imageClassifier,
            imageLoader,
            actionFactory,
            transitionCallback,
            featureFlagRepository
        )
        assertThat(testSubject.preferredContentPreview)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
        verify(transitionCallback, times(1)).onAllTransitionElementsReady()
    }
}
