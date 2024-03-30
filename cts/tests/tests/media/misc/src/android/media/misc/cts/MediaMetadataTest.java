/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.media.misc.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.cts.NonMediaMainlineTest;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * Tests {@link MediaMetadata}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@NonMediaMainlineTest
public class MediaMetadataTest {

    @Test
    public void builder_defaultConstructor_hasNoData() {
        MediaMetadata metadata = new MediaMetadata.Builder().build();

        assertEquals(0, metadata.size());
        assertTrue(metadata.keySet().isEmpty());
    }

    @Test
    public void builder_putText() {
        String testTitle = "test_title";
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putText(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .build();

        assertTrue(metadata.containsKey(MediaMetadata.METADATA_KEY_TITLE));
        CharSequence titleOut = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        assertTrue(TextUtils.equals(testTitle, titleOut));
    }

    @Test
    public void builder_putString() {
        String testTitle = "test_title";
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .build();

        assertTrue(metadata.containsKey(MediaMetadata.METADATA_KEY_TITLE));
        String titleOut = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        assertTrue(TextUtils.equals(testTitle, titleOut));
    }

    @Test
    public void builder_putLong() {
        long testYear = 2021;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        assertTrue(metadata.containsKey(MediaMetadata.METADATA_KEY_YEAR));
        long yearOut = metadata.getLong(MediaMetadata.METADATA_KEY_YEAR);
        assertEquals(testYear, yearOut);
    }

    @Test
    public void builder_putRating() {
        Rating testHeartRating = Rating.newHeartRating(/*hasHeart=*/ true);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putRating(MediaMetadata.METADATA_KEY_RATING, testHeartRating)
                .build();

        assertTrue(metadata.containsKey(MediaMetadata.METADATA_KEY_RATING));
        Rating ratingOut = metadata.getRating(MediaMetadata.METADATA_KEY_RATING);
        assertEquals(testHeartRating, ratingOut);
    }

    @Test
    public void builder_putText_throwsIAE_withNonTextKey() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        try {
            builder.putText(MediaMetadata.METADATA_KEY_YEAR, "test");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void builder_putString_throwsIAE_withNonTextKey() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        try {
            builder.putString(MediaMetadata.METADATA_KEY_YEAR, "test");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void builder_putLong_throwsIAE_withNonLongKey() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        try {
            builder.putLong(MediaMetadata.METADATA_KEY_TITLE, 2021);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void builder_putRating_throwsIAE_withNonRatingKey() {
        Rating testHeartRating = Rating.newHeartRating(/*hasHeart=*/ true);
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        try {
            builder.putRating(MediaMetadata.METADATA_KEY_TITLE, testHeartRating);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void builder_putBitmap_throwsIAE_withNonBitmapKey() {
        Bitmap testBitmap = Bitmap.createBitmap(/*width=*/ 16, /*height=*/16,
                Bitmap.Config.ARGB_8888);
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        try {
            builder.putBitmap(MediaMetadata.METADATA_KEY_TITLE, testBitmap);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void builder_copyConstructor() {
        long testYear = 2021;
        MediaMetadata originalMetadata = new MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        MediaMetadata copiedMetadata = new MediaMetadata.Builder(originalMetadata).build();
        assertEquals(originalMetadata, copiedMetadata);
    }

    @Test
    public void equalsAndHashCode() {
        String testTitle = "test_title";
        long testYear = 2021;
        MediaMetadata originalMetadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();
        MediaMetadata metadataToCompare = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        assertEquals(originalMetadata, metadataToCompare);
        assertEquals(originalMetadata.hashCode(), metadataToCompare.hashCode());
    }

    @Test
    public void equalsAndHashCode_ignoreRatingAndBitmap() {
        Rating testHeartRating = Rating.newHeartRating(/*hasHeart=*/ true);
        Bitmap testBitmap = Bitmap.createBitmap(/*width=*/ 16, /*height=*/16,
                Bitmap.Config.ARGB_8888);
        MediaMetadata originalMetadata = new MediaMetadata.Builder()
                .putRating(MediaMetadata.METADATA_KEY_RATING, testHeartRating)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, testBitmap)
                .build();
        MediaMetadata emptyMetadata = new MediaMetadata.Builder().build();

        assertEquals(originalMetadata, emptyMetadata);
        assertEquals(originalMetadata.hashCode(), emptyMetadata.hashCode());
    }

    @Test
    public void sizeAndKeySet() {
        Rating testHeartRating = Rating.newHeartRating(/*hasHeart=*/ true);
        Bitmap testBitmap = Bitmap.createBitmap(/*width=*/ 16, /*height=*/16,
                Bitmap.Config.ARGB_8888);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putRating(MediaMetadata.METADATA_KEY_RATING, testHeartRating)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, testBitmap)
                .build();

        assertEquals(2, metadata.size());
        Set<String> keySet = metadata.keySet();
        assertEquals(2, keySet.size());
        assertTrue(keySet.contains(MediaMetadata.METADATA_KEY_RATING));
        assertTrue(keySet.contains(MediaMetadata.METADATA_KEY_ART));
    }

    @Test
    public void describeContents() {
        long testYear = 2021;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        assertEquals(0, metadata.describeContents());
    }

    @Test
    public void writeToParcel() {
        String testTitle = "test_title";
        long testYear = 2021;
        MediaMetadata originalMetadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, testTitle)
                .putLong(MediaMetadata.METADATA_KEY_YEAR, testYear)
                .build();

        Parcel parcel = Parcel.obtain();
        originalMetadata.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        MediaMetadata metadataOut = MediaMetadata.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(originalMetadata, metadataOut);
    }

    @Test
    public void getDescription() {
        String testMediaId = "media_id";
        String testTitle = "test_title";
        String testSubtitle = "test_subtitle";
        String testDescription = "test_description";
        Bitmap testIcon = Bitmap.createBitmap(/*width=*/ 16, /*height=*/16,
                Bitmap.Config.ARGB_8888);
        String testMediaUri = "https://www.google.com";
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, testMediaId)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, testTitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, testSubtitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, testDescription)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, testIcon)
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, testMediaUri)
                .build();

        MediaDescription mediaDescription = metadata.getDescription();
        assertTrue(TextUtils.equals(testMediaId, mediaDescription.getMediaId()));
        assertTrue(TextUtils.equals(testTitle, mediaDescription.getTitle()));
        assertTrue(TextUtils.equals(testSubtitle, mediaDescription.getSubtitle()));
        assertTrue(TextUtils.equals(testDescription, mediaDescription.getDescription()));
        assertNotNull(mediaDescription.getIconBitmap());
        assertTrue(TextUtils.equals(testMediaUri, mediaDescription.getMediaUri().toString()));
    }

    @Test
    public void getBitmapDimensionLimit_returnsIntegerMaxWhenNotSet() {
        MediaMetadata metadata = new MediaMetadata.Builder().build();
        assertEquals(Integer.MAX_VALUE, metadata.getBitmapDimensionLimit());
    }

    @Test
    public void builder_setBitmapDimensionLimit_bitmapsAreScaledDown() {
        // A large bitmap (64MB).
        final int originalWidth = 4096;
        final int originalHeight = 4096;
        Bitmap testBitmap = Bitmap.createBitmap(
                originalWidth, originalHeight, Bitmap.Config.ARGB_8888);

        final int testBitmapDimensionLimit = 16;

        MediaMetadata metadata = new MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, testBitmap)
                .setBitmapDimensionLimit(testBitmapDimensionLimit)
                .build();
        assertEquals(testBitmapDimensionLimit, metadata.getBitmapDimensionLimit());

        Bitmap scaledDownBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        assertNotNull(scaledDownBitmap);
        assertTrue(scaledDownBitmap.getWidth() <= testBitmapDimensionLimit);
        assertTrue(scaledDownBitmap.getHeight() <= testBitmapDimensionLimit);
    }

    @Test
    public void builder_setBitmapDimensionLimit_bitmapsAreNotScaledDown() {
        // A small bitmap.
        final int originalWidth = 16;
        final int originalHeight = 16;
        Bitmap testBitmap = Bitmap.createBitmap(
                originalWidth, originalHeight, Bitmap.Config.ARGB_8888);

        // The limit is larger than the width/height.
        final int testBitmapDimensionLimit = 256;

        MediaMetadata metadata = new MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, testBitmap)
                .setBitmapDimensionLimit(testBitmapDimensionLimit)
                .build();
        assertEquals(testBitmapDimensionLimit, metadata.getBitmapDimensionLimit());

        Bitmap notScaledDownBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        assertNotNull(notScaledDownBitmap);
        assertEquals(originalWidth, notScaledDownBitmap.getWidth());
        assertEquals(originalHeight, notScaledDownBitmap.getHeight());
    }

    @Test
    public void builder_setMaxBitmapDimensionLimit_unsetLimit() {
        final int testBitmapDimensionLimit = 256;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setBitmapDimensionLimit(testBitmapDimensionLimit)
                .build();
        assertEquals(testBitmapDimensionLimit, metadata.getBitmapDimensionLimit());

        // Using copy constructor, unset the limit by passing Integer.MAX_VALUE to the limit.
        MediaMetadata copiedMetadataWithLimitUnset = new MediaMetadata.Builder()
                .setBitmapDimensionLimit(Integer.MAX_VALUE)
                .build();
        assertEquals(Integer.MAX_VALUE, copiedMetadataWithLimitUnset.getBitmapDimensionLimit());
    }

}
