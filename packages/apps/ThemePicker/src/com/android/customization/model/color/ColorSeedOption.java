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
package com.android.customization.model.color;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PorterDuff.Mode;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.VisibleForTesting;

import com.android.customization.model.color.ColorOptionsProvider.ColorSource;
import com.android.systemui.monet.Style;
import com.android.wallpaper.R;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a seed color obtained from WallpaperColors, for the user to chose as their theming
 * option.
 */
public class ColorSeedOption extends ColorOption {

    private final PreviewInfo mPreviewInfo;
    @ColorSource
    private final String mSource;

    @VisibleForTesting
    ColorSeedOption(String title, Map<String, String> overlayPackages, boolean isDefault,
            @ColorSource String source, Style style, int index, PreviewInfo previewInfo) {
        super(title, overlayPackages, isDefault, style, index);
        mSource = source;
        mPreviewInfo = previewInfo;
    }

    @Override
    public PreviewInfo getPreviewInfo() {
        return mPreviewInfo;
    }

    @Override
    public String getSource() {
        return mSource;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.color_option;
    }

    @Override
    public void bindThumbnailTile(View view) {
        Resources res = view.getContext().getResources();
        @ColorInt int[] colors = mPreviewInfo.resolveColors(res);

        for (int i = 0; i < mPreviewColorIds.length; i++) {
            ImageView colorPreviewImageView = view.findViewById(mPreviewColorIds[i]);
            colorPreviewImageView.getDrawable().setColorFilter(colors[i], Mode.SRC);
        }

        view.setContentDescription(getContentDescription(view.getContext()));
    }

    @Override
    public CharSequence getContentDescription(Context context) {
        // Override because we want all options with the same description.
        return context.getString(R.string.wallpaper_color_title);
    }

    /**
     * The preview information of {@link ColorOption}
     */
    public static class PreviewInfo implements ColorOption.PreviewInfo {
        @ColorInt public int[] lightColors;
        @ColorInt public int[] darkColors;

        private PreviewInfo(@ColorInt int[] lightColors, @ColorInt int[] darkColors) {
            this.lightColors = lightColors;
            this.darkColors = darkColors;
        }

        /**
         * Returns the colors to be applied corresponding with the current
         * configuration's UI mode.
         * @return one of {@link #lightColors} or {@link #darkColors}
         */
        @ColorInt
        public int[] resolveColors(Resources res) {
            boolean night = (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            return night ? darkColors : lightColors;
        }
    }

    /**
     * The builder of ColorSeedOption
     */
    public static class Builder {
        protected String mTitle;
        @ColorInt
        private int[] mLightColors;
        @ColorInt
        private int[] mDarkColors;
        @ColorSource
        private String mSource;
        private boolean mIsDefault;
        private Style mStyle = Style.TONAL_SPOT;
        private int mIndex;
        protected Map<String, String> mPackages = new HashMap<>();

        /**
         * Builds the ColorSeedOption
         * @return new {@link ColorOption} object
         */
        public ColorSeedOption build() {
            return new ColorSeedOption(mTitle, mPackages, mIsDefault, mSource, mStyle, mIndex,
                    createPreviewInfo());
        }

        /**
         * Creates preview information
         * @return the {@link PreviewInfo} object
         */
        public PreviewInfo createPreviewInfo() {
            return new PreviewInfo(mLightColors, mDarkColors);
        }

        public Map<String, String> getPackages() {
            return Collections.unmodifiableMap(mPackages);
        }

        /**
         * Gets title of {@link ColorOption} object
         * @return title string
         */
        public String getTitle() {
            return mTitle;
        }

        /**
         * Sets title of bundle
         * @param title specified title
         * @return this of {@link ColorBundle.Builder}
         */
        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        /**
         * Sets the colors for preview in light mode
         * @param lightColors  {@link ColorInt} colors for light mode
         * @return this of {@link Builder}
         */
        public Builder setLightColors(@ColorInt int[] lightColors) {
            mLightColors = lightColors;
            return this;
        }

        /**
         * Sets the colors for preview in light mode
         * @param darkColors  {@link ColorInt} colors for light mode
         * @return this of {@link Builder}
         */
        public Builder setDarkColors(@ColorInt int[] darkColors) {
            mDarkColors = darkColors;
            return this;
        }


        /**
         * Sets overlay package for bundle
         * @param category the category of bundle
         * @param packageName tha name of package in the category
         * @return this of {@link Builder}
         */
        public Builder addOverlayPackage(String category, String packageName) {
            mPackages.put(category, packageName);
            return this;
        }

        /**
         * Sets the source of this color seed
         * @param source typically either {@link ColorOptionsProvider#COLOR_SOURCE_HOME} or
         *              {@link ColorOptionsProvider#COLOR_SOURCE_LOCK}
         * @return this of {@link Builder}
         */
        public Builder setSource(@ColorSource String source) {
            mSource = source;
            return this;
        }

        /**
         * Sets the source of this color seed
         * @param style color style of {@link Style}
         * @return this of {@link Builder}
         */
        public Builder setStyle(Style style) {
            mStyle = style;
            return this;
        }

        /**
         * Sets color option index of seed
         * @param index color option index
         * @return this of {@link ColorBundle.Builder}
         */
        public Builder setIndex(int index) {
            mIndex = index;
            return this;
        }

        /**
         * Sets as default bundle
         * @return this of {@link Builder}
         */
        public Builder asDefault() {
            mIsDefault = true;
            return this;
        }
    }
}
