package com.android.customization.picker.clock.ui.fragment

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.module.ThemePickerInjector
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.plugins.ClockMetadata
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.wallpaper.R
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.AppbarFragment

class ClockCustomDemoFragment : AppbarFragment() {
    @VisibleForTesting lateinit var recyclerView: RecyclerView
    @VisibleForTesting lateinit var clockRegistry: ClockRegistry

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_clock_custom_picker_demo, container, false)
        setUpToolbar(view)
        clockRegistry =
            (InjectorProvider.getInjector() as ThemePickerInjector).getClockRegistry(
                requireContext()
            )
        val listInUse = clockRegistry.getClocks().filter { "NOT_IN_USE" !in it.clockId }

        recyclerView = view.requireViewById(R.id.clock_preview_card_list_demo)
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        recyclerView.adapter =
            ClockRecyclerAdapter(listInUse, requireContext()) {
                clockRegistry.currentClockId = it.clockId
                Toast.makeText(context, "${it.name} selected", Toast.LENGTH_SHORT).show()
            }
        return view
    }

    override fun getDefaultTitle(): CharSequence {
        return getString(R.string.clock_title)
    }

    internal class ClockRecyclerAdapter(
        val list: List<ClockMetadata>,
        val context: Context,
        val onClockSelected: (ClockMetadata) -> Unit
    ) : RecyclerView.Adapter<ClockRecyclerAdapter.ViewHolder>() {
        class ViewHolder(val view: View, val textView: TextView, val onItemClicked: (Int) -> Unit) :
            RecyclerView.ViewHolder(view) {
            init {
                itemView.setOnClickListener { onItemClicked(absoluteAdapterPosition) }
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val rootView = FrameLayout(viewGroup.context)
            val textView =
                TextView(ContextThemeWrapper(viewGroup.context, R.style.SectionTitleTextStyle))
            textView.setPadding(ITEM_PADDING)
            rootView.addView(textView)
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            rootView.setBackgroundResource(outValue.resourceId)
            val lp = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            rootView.layoutParams = lp
            return ViewHolder(rootView, textView) { onClockSelected(list[it]) }
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.textView.text = list[position].name
        }

        override fun getItemCount() = list.size

        companion object {
            val ITEM_PADDING = 40
        }
    }
}
