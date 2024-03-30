package com.android.customization.model.grid.ui.binder

import android.widget.ImageView
import com.android.customization.model.grid.ui.viewmodel.GridIconViewModel
import com.android.customization.widget.GridTileDrawable

object GridIconViewBinder {
    fun bind(view: ImageView, viewModel: GridIconViewModel) {
        view.setImageDrawable(
            GridTileDrawable(
                viewModel.columns,
                viewModel.rows,
                viewModel.path,
            )
        )
    }
}
