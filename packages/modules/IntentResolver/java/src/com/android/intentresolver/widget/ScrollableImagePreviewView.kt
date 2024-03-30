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

package com.android.intentresolver.widget

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.intentresolver.R
import com.android.intentresolver.widget.ImagePreviewView.TransitionElementStatusCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

private const val TRANSITION_NAME = "screenshot_preview_image"

class ScrollableImagePreviewView : RecyclerView, ImagePreviewView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = Adapter(context)
        val spacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 5f, context.resources.displayMetrics
        ).toInt()
        addItemDecoration(SpacingDecoration(spacing))
    }

    private val previewAdapter get() = adapter as Adapter

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setOverScrollMode(
            if (areAllChildrenVisible) View.OVER_SCROLL_NEVER else View.OVER_SCROLL_ALWAYS
        )
    }

    override fun setTransitionElementStatusCallback(callback: TransitionElementStatusCallback?) {
        previewAdapter.transitionStatusElementCallback = callback
    }

    override fun setImages(uris: List<Uri>, imageLoader: ImageLoader) {
        previewAdapter.setImages(uris, imageLoader)
    }

    private class Adapter(private val context: Context) : RecyclerView.Adapter<ViewHolder>() {
        private val uris = ArrayList<Uri>()
        private var imageLoader: ImageLoader? = null
        var transitionStatusElementCallback: TransitionElementStatusCallback? = null

        fun setImages(uris: List<Uri>, imageLoader: ImageLoader) {
            this.uris.clear()
            this.uris.addAll(uris)
            this.imageLoader = imageLoader
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, itemType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(context)
                    .inflate(R.layout.image_preview_image_item, parent, false)
            )
        }

        override fun getItemCount(): Int = uris.size

        override fun onBindViewHolder(vh: ViewHolder, position: Int) {
            vh.bind(
                uris[position],
                imageLoader ?: error("ImageLoader is missing"),
                if (position == 0 && transitionStatusElementCallback != null) {
                    this::onTransitionElementReady
                } else {
                    null
                }
            )
        }

        override fun onViewRecycled(vh: ViewHolder) {
            vh.unbind()
        }

        override fun onFailedToRecycleView(vh: ViewHolder): Boolean {
            vh.unbind()
            return super.onFailedToRecycleView(vh)
        }

        private fun onTransitionElementReady(name: String) {
            transitionStatusElementCallback?.apply {
                onTransitionElementReady(name)
                onAllTransitionElementsReady()
            }
            transitionStatusElementCallback = null
        }
    }

    private class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image = view.requireViewById<ImageView>(R.id.image)
        private var scope: CoroutineScope? = null

        fun bind(
            uri: Uri,
            imageLoader: ImageLoader,
            previewReadyCallback: ((String) -> Unit)?
        ) {
            image.setImageDrawable(null)
            image.transitionName = if (previewReadyCallback != null) {
                TRANSITION_NAME
            } else {
                null
            }
            resetScope().launch {
                loadImage(uri, imageLoader, previewReadyCallback)
            }
        }

        private suspend fun loadImage(
            uri: Uri,
            imageLoader: ImageLoader,
            previewReadyCallback: ((String) -> Unit)?
        ) {
            val bitmap = runCatching {
                // it's expected for all loading/caching optimizations to be implemented by the
                // loader
                imageLoader(uri)
            }.getOrNull()
            image.setImageBitmap(bitmap)
            previewReadyCallback?.let { callback ->
                image.waitForPreDraw()
                callback(TRANSITION_NAME)
            }
        }

        private fun resetScope(): CoroutineScope =
            (MainScope() + Dispatchers.Main.immediate).also {
                scope?.cancel()
                scope = it
            }

        fun unbind() {
            scope?.cancel()
            scope = null
        }
    }

    private class SpacingDecoration(private val margin: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: State) {
            outRect.set(margin, 0, margin, 0)
        }
    }
}
