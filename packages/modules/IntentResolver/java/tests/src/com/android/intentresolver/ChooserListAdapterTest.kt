/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ResolveInfoFlags
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.ChooserListAdapter.LoadDirectShareIconTask
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.chooser.SelectableTargetInfo
import com.android.intentresolver.chooser.TargetInfo
import com.android.internal.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
class ChooserListAdapterTest {
    private val packageManager = mock<PackageManager> {
        whenever(
            resolveActivity(any(), any<ResolveInfoFlags>())
        ).thenReturn(mock())
    }
    private val context = InstrumentationRegistry.getInstrumentation().getContext()
    private val resolverListController = mock<ResolverListController>()
    private val chooserActivityLogger = mock<ChooserActivityLogger>()

    private fun createChooserListAdapter(
        taskProvider: (TargetInfo?) -> LoadDirectShareIconTask
    ) = object : ChooserListAdapter(
            context,
            emptyList(),
            emptyArray(),
            emptyList(),
            false,
            resolverListController,
            null,
            Intent(),
            mock(),
            packageManager,
            chooserActivityLogger,
            mock(),
            0
        ) {
            override fun createLoadDirectShareIconTask(
                info: SelectableTargetInfo
            ): LoadDirectShareIconTask = taskProvider(info)
        }

    @Before
    fun setup() {
        // ChooserListAdapter reads DeviceConfig and needs a permission for that.
        InstrumentationRegistry
            .getInstrumentation()
            .getUiAutomation()
            .adoptShellPermissionIdentity("android.permission.READ_DEVICE_CONFIG")
    }

    @Test
    fun testDirectShareTargetLoadingIconIsStarted() {
        val view = createView()
        val viewHolder = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolder
        val targetInfo = createSelectableTargetInfo()
        val iconTask = mock<LoadDirectShareIconTask>()
        val testSubject = createChooserListAdapter { iconTask }
        testSubject.onBindView(view, targetInfo, 0)

        verify(iconTask, times(1)).loadIcon()
    }

    @Test
    fun testOnlyOneTaskPerTarget() {
        val view = createView()
        val viewHolderOne = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolderOne
        val targetInfo = createSelectableTargetInfo()
        val iconTaskOne = mock<LoadDirectShareIconTask>()
        val testTaskProvider = mock<() -> LoadDirectShareIconTask> {
            whenever(invoke()).thenReturn(iconTaskOne)
        }
        val testSubject = createChooserListAdapter { testTaskProvider.invoke() }
        testSubject.onBindView(view, targetInfo, 0)

        val viewHolderTwo = ResolverListAdapter.ViewHolder(view)
        view.tag = viewHolderTwo
        whenever(testTaskProvider()).thenReturn(mock())

        testSubject.onBindView(view, targetInfo, 0)

        verify(iconTaskOne, times(1)).loadIcon()
        verify(testTaskProvider, times(1)).invoke()
    }

    private fun createSelectableTargetInfo(): TargetInfo =
        SelectableTargetInfo.newSelectableTargetInfo(
            /* sourceInfo = */ DisplayResolveInfo.newDisplayResolveInfo(
                Intent(),
                ResolverDataProvider.createResolveInfo(2, 0),
                "label",
                "extended info",
                Intent(),
                /* resolveInfoPresentationGetter= */ null
            ),
            /* backupResolveInfo = */ mock(),
            /* resolvedIntent = */ Intent(),
            /* chooserTarget = */ createChooserTarget(
                "Target", 0.5f, ComponentName("pkg", "Class"), "id-1"
            ),
            /* modifiedScore = */ 1f,
            /* shortcutInfo = */ createShortcutInfo("id-1", ComponentName("pkg", "Class"), 1),
            /* appTarget */ null,
            /* referrerFillInIntent = */ Intent()
        )

    private fun createView(): View {
        val view = FrameLayout(context)
        TextView(context).apply {
            id = R.id.text1
            view.addView(this)
        }
        TextView(context).apply {
            id = R.id.text2
            view.addView(this)
        }
        ImageView(context).apply {
            id = R.id.icon
            view.addView(this)
        }
        return view
    }
}
