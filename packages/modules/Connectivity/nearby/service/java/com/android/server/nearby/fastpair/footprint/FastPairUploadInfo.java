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

package com.android.server.nearby.fastpair.footprint;


import com.google.protobuf.ByteString;

import service.proto.Cache;

/**
 * Wrapper class that upload the pair info to the footprint.
 */
public class FastPairUploadInfo {

    private Cache.StoredDiscoveryItem mStoredDiscoveryItem;

    private ByteString mAccountKey;

    private  ByteString mSha256AccountKeyPublicAddress;


    public FastPairUploadInfo(Cache.StoredDiscoveryItem storedDiscoveryItem, ByteString accountKey,
            ByteString sha256AccountKeyPublicAddress) {
        mStoredDiscoveryItem = storedDiscoveryItem;
        mAccountKey = accountKey;
        mSha256AccountKeyPublicAddress = sha256AccountKeyPublicAddress;
    }

    public Cache.StoredDiscoveryItem getStoredDiscoveryItem() {
        return mStoredDiscoveryItem;
    }

    public ByteString getAccountKey() {
        return mAccountKey;
    }


    public ByteString getSha256AccountKeyPublicAddress() {
        return mSha256AccountKeyPublicAddress;
    }
}
