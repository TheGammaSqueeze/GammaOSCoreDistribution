package com.android.customization.picker.clock.ui.fragment

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.systemui.plugins.ClockMetadata
import com.android.systemui.plugins.ClockSettings
import com.android.systemui.plugins.PluginManager
import com.android.systemui.shared.clocks.ClockRegistry
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests of [ClockCustomDemoFragment]. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@Ignore("b/270606895")
class ClockCustomDemoFragmentTest {
    private lateinit var mActivity: AppCompatActivity
    private var mClockCustomDemoFragment: ClockCustomDemoFragment? = null
    @Mock private lateinit var registry: ClockRegistry
    @Mock private lateinit var mockPluginManager: PluginManager

    private var settingValue: ClockSettings? = null

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mActivity = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        mClockCustomDemoFragment = ClockCustomDemoFragment()
        whenever(registry.getClocks())
            .thenReturn(
                listOf(
                    ClockMetadata("CLOCK_1", "Clock 1"),
                    ClockMetadata("CLOCK_2", "Clock 2"),
                    ClockMetadata("CLOCK_NOT_IN_USE", "Clock not in use")
                )
            )

        mClockCustomDemoFragment!!.clockRegistry = registry
        mClockCustomDemoFragment!!.recyclerView = RecyclerView(mActivity)
        mClockCustomDemoFragment!!.recyclerView.layoutManager =
            LinearLayoutManager(mActivity, RecyclerView.VERTICAL, false)
    }

    @Test
    fun testItemCount_getCorrectClockCount() {
        Assert.assertEquals(3, mClockCustomDemoFragment!!.recyclerView.adapter!!.itemCount)
    }

    @Test
    fun testClick_setCorrectClockId() {
        mClockCustomDemoFragment!!
            .recyclerView
            .measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        mClockCustomDemoFragment!!.recyclerView.layout(0, 0, 100, 10000)
        val testPosition = 1
        mClockCustomDemoFragment!!
            .recyclerView
            .findViewHolderForAdapterPosition(testPosition)
            ?.itemView
            ?.performClick()
        verify(registry).currentClockId = "CLOCK_1"
    }
}
