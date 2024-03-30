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

package com.android.intentresolver

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.util.Size
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class ImagePreviewImageLoaderTest {
    private val imageSize = Size(300, 300)
    private val uriOne = Uri.parse("content://org.package.app/image-1.png")
    private val uriTwo = Uri.parse("content://org.package.app/image-2.png")
    private val contentResolver = mock<ContentResolver>()
    private val resources = mock<Resources> {
        whenever(getDimensionPixelSize(R.dimen.chooser_preview_image_max_dimen))
            .thenReturn(imageSize.width)
    }
    private val context = mock<Context> {
        whenever(this.resources).thenReturn(this@ImagePreviewImageLoaderTest.resources)
        whenever(this.contentResolver).thenReturn(this@ImagePreviewImageLoaderTest.contentResolver)
    }
    private val scheduler = TestCoroutineScheduler()
    private val lifecycleOwner = TestLifecycleOwner()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val testSubject = ImagePreviewImageLoader(
        context, lifecycleOwner.lifecycle, 1, dispatcher
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        lifecycleOwner.state = Lifecycle.State.CREATED
    }

    @After
    fun cleanup() {
        lifecycleOwner.state = Lifecycle.State.DESTROYED
        Dispatchers.resetMain()
    }

    @Test
    fun test_prePopulate() = runTest {
        testSubject.prePopulate(listOf(uriOne, uriTwo))

        verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
        verify(contentResolver, never()).loadThumbnail(uriTwo, imageSize, null)

        testSubject(uriOne)
        verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
    }

    @Test
    fun test_invoke_return_cached_image() = runTest {
        testSubject(uriOne)
        testSubject(uriOne)

        verify(contentResolver, times(1)).loadThumbnail(any(), any(), anyOrNull())
    }

    @Test
    fun test_invoke_old_records_evicted_from_the_cache() = runTest {
        testSubject(uriOne)
        testSubject(uriTwo)
        testSubject(uriTwo)
        testSubject(uriOne)

        verify(contentResolver, times(2)).loadThumbnail(uriOne, imageSize, null)
        verify(contentResolver, times(1)).loadThumbnail(uriTwo, imageSize, null)
    }
}
