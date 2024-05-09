/*
 * Copyright 2009 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/bitmap.h>
#include "RkHandWriting_JNI.h"
#include "HandWritingManager.h"

using namespace std;

static HandWritingManager *mHandWritingManager;

JNIEXPORT jint JNICALL native_init(JNIEnv * env, jobject thiz, jobject jrect,
        jint viewWidth, jint viewHeight, jint screenWidth, jint screenHeight,
        jint layerStack)
{
    LOGW("");
    jclass clazz = env->GetObjectClass(jrect);
    if (0 == clazz) {
        LOGE("GetObjectClass returned 0");
        return JNI_ERR;
    }

    jfieldID jfid;
    jfid = (env)->GetFieldID(clazz, "left", "I");
    int left = (int)env->GetIntField(jrect, jfid);

    jfid = (env)->GetFieldID(clazz, "top", "I");
    int top = (int)env->GetIntField(jrect, jfid);

    jfid = (env)->GetFieldID(clazz, "right", "I");
    int right = (int)env->GetIntField(jrect, jfid);

    jfid = (env)->GetFieldID(clazz, "bottom", "I");
    int bottom = (int)env->GetIntField(jrect, jfid);

    mHandWritingManager = new HandWritingManager();
    int ret = mHandWritingManager->init(left, top, right, bottom, viewWidth, viewHeight, screenWidth, screenHeight, layerStack);

    return ret;
}

JNIEXPORT jint JNICALL native_clear(JNIEnv * env, jobject thiz)
{
    LOGW("");
    mHandWritingManager->clear();
    return JNI_OK;
}

JNIEXPORT jint JNICALL native_exit(JNIEnv * env, jobject thiz)
{
    LOGW("");
    mHandWritingManager->exit();
    delete mHandWritingManager;
    mHandWritingManager = nullptr;
    return JNI_OK;
}

JNIEXPORT jint JNICALL native_draw_bitmap(JNIEnv * env, jobject thiz, jobject bitmap) {
    LOGW("");
    AndroidBitmapInfo info;
    void* pixels;

    if(AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("AndroidBitmap_getInfo failed");
    }

    int width = (int) info.width;
    int height = (int) info.height;
    LOGD("native_draw_bitmap() width:%d,height:%d", width, height);
    AndroidBitmap_lockPixels(env, bitmap, (void**) &pixels);

    if(AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("AndroidBitmap_getInfo failed");
    }
    mHandWritingManager->drawBitmap(pixels, width, height);

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_OK;
}

//======================================================================================

static JNINativeMethod gMethods[] = {
    { "native_init", "(Landroid/graphics/Rect;IIIII)I", (void*) native_init },
    { "native_clear", "()I", (void*) native_clear },
    { "native_exit", "()I", (void*) native_exit },
    { "native_draw_bitmap", "(Landroid/graphics/Bitmap;)I", (void*) native_draw_bitmap }
};

static int registerNativeMethods(JNIEnv* env, const char* className,
                                 JNINativeMethod* gMethods, int numMethods) {
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE("---------clazz is null");
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        LOGE("---------RegisterNatives < 0");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * System.loadLibrary("libxxx")
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGW("");
    JNIEnv* env = NULL;

    jint ret = vm->GetEnv((void**) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        LOGE("GetEnv JNI_VERSION_1_6 failed");
        return JNI_ERR;
    }
    assert(env != NULL);

    const char* kClassName = "com/rockchip/handwritingdemo/RkHandWritingJNI";
    ret = registerNativeMethods(env, kClassName, gMethods,
        sizeof(gMethods) / sizeof(gMethods[0]));

    if (ret != JNI_TRUE) {
        LOGE("registerNatives failed");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGW("");
    JNIEnv* env = NULL;
    jint ret = vm->GetEnv((void**) &env, JNI_VERSION_1_6);
    LOGE("ret=%d", ret);
}
