package com.android.customization.model.clock;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.clock.Clockface.Builder;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.ContentUriAsset;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContentProviderClockProvider implements ClockProvider {

    private static final String TAG = "ContentProviderClockProvider";
    private static final ExecutorService sExecutorService = Executors.newSingleThreadExecutor();
    private static final String LIST_OPTIONS = "list_options";
    private static final String COL_TITLE = "title";
    private static final String COL_ID = "id";
    private static final String COL_THUMBNAIL = "thumbnail";
    private static final String COL_PREVIEW = "preview";

    private final Context mContext;
    private final ProviderInfo mProviderInfo;
    private List<Clockface> mClocks;
    private boolean mClockContentAvailable;

    public ContentProviderClockProvider(Context context) {
        mContext = context;
        String providerAuthority = mContext.getString(R.string.clocks_provider_authority);
        // TODO: check permissions if needed
        mProviderInfo = TextUtils.isEmpty(providerAuthority) ? null
                : mContext.getPackageManager().resolveContentProvider(providerAuthority,
                        PackageManager.MATCH_SYSTEM_ONLY);

        if (TextUtils.isEmpty(mContext.getString(R.string.clocks_stub_package))) {
            mClockContentAvailable = false;
        } else {
            try {
                ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(
                        mContext.getString(R.string.clocks_stub_package),
                        PackageManager.MATCH_SYSTEM_ONLY);
                mClockContentAvailable = applicationInfo != null;
            } catch (NameNotFoundException e) {
                mClockContentAvailable = false;
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return mProviderInfo != null && mClockContentAvailable
                && (mClocks == null || !mClocks.isEmpty());
    }

    @Override
    public void fetch(OptionsFetchedListener<Clockface> callback, boolean reload) {
        if (!isAvailable()) {
            if (callback != null) {
                callback.onError(null);
            }
            return;
        }
        if (mClocks != null && !reload) {
            if (callback != null) {
                if (!mClocks.isEmpty()) {
                    callback.onOptionsLoaded(mClocks);
                } else {
                    callback.onError(null);
                }
            }
            return;
        }
        sExecutorService.submit(() -> {
            Uri optionsUri = new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(mProviderInfo.authority)
                    .appendPath(LIST_OPTIONS)
                    .build();

            ContentResolver resolver = mContext.getContentResolver();

            List<Clockface> clockfaces = new ArrayList<>();
            try (Cursor c = resolver.query(optionsUri, null, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    String id = c.getString(c.getColumnIndex(COL_ID));
                    String title = c.getString(c.getColumnIndex(COL_TITLE));
                    String thumbnailUri = c.getString(c.getColumnIndex(COL_THUMBNAIL));
                    String previewUri = c.getString(c.getColumnIndex(COL_PREVIEW));
                    Uri thumbnail = Uri.parse(thumbnailUri);
                    Uri preview = Uri.parse(previewUri);

                    Clockface.Builder builder = new Builder();
                    builder.setId(id).setTitle(title)
                            .setThumbnail(new ContentUriAsset(mContext, thumbnail,
                                    RequestOptions.fitCenterTransform()))
                            .setPreview(new ContentUriAsset(mContext, preview,
                                    RequestOptions.fitCenterTransform()));
                    clockfaces.add(builder.build());
                }
                Glide.get(mContext).clearDiskCache();
            } catch (Exception e) {
                clockfaces = null;
                Log.e(TAG, "Failed to query clock face options.", e);
            }
            final List<Clockface> clockfaceList = clockfaces;
            new Handler(Looper.getMainLooper()).post(() -> {
                mClocks = clockfaceList;
                if (callback != null) {
                    if (!mClocks.isEmpty()) {
                        callback.onOptionsLoaded(mClocks);
                    } else {
                        callback.onError(null);
                    }
                }
            });
        });
    }
}
