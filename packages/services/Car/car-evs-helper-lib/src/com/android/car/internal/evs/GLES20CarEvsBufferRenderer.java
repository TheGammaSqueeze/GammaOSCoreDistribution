/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.internal.evs;

import static android.opengl.GLU.gluErrorString;

import android.annotation.NonNull;
import android.car.evs.CarEvsBufferDescriptor;
import android.content.Context;
import android.hardware.HardwareBuffer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GLES20 SurfaceView Renderer for CarEvsBufferDescriptor.
 */
public final class GLES20CarEvsBufferRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = GLES20CarEvsBufferRenderer.class.getSimpleName()
            .replace("GLES20", "");
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int FLOAT_SIZE_BYTES = 4;

    private static final float[] sVertCarPosData = {
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f };

    private static final float[] sVertCarTexData = {
           -0.5f, -0.5f,
            0.5f, -0.5f,
           -0.5f,  0.5f,
            0.5f,  0.5f };

    private static final float[] sIdentityMatrix = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f };

    private final String mVertexShader =
        "attribute vec4 pos;                    \n" +
        "attribute vec2 tex;                    \n" +
        "uniform mat4 cameraMat;                \n" +
        "varying vec2 uv;                       \n" +
        "void main()                            \n" +
        "{                                      \n" +
        "   gl_Position = cameraMat * pos;      \n" +
        "   uv = tex;                           \n" +
        "}                                      \n";

    private final String mFragmentShader =
        "precision mediump float;               \n" +
        "uniform sampler2D tex;                 \n" +
        "varying vec2 uv;                       \n" +
        "void main()                            \n" +
        "{                                      \n" +
        "    gl_FragColor = texture2D(tex, uv); \n" +
        "}                                      \n";

    private final Object mLock = new Object();
    private final CarEvsGLSurfaceView.BufferCallback mCallback;
    private final Context mContext;
    private final FloatBuffer mVertCarPos;
    private final FloatBuffer mVertCarTex;

    private int mProgram;
    private int mTextureId;
    private int mWidth;
    private int mHeight;

    // Native method to update the texture with a received frame buffer
    @GuardedBy("mLock")
    private CarEvsBufferDescriptor mBufferInUse;

    /** Load jni on initialization. */
    static {
        System.loadLibrary("carevsglrenderer_jni");
    }

    public GLES20CarEvsBufferRenderer(@NonNull Context context,
            @NonNull CarEvsGLSurfaceView.BufferCallback callback, int angleInDegree) {

        Preconditions.checkArgument(context != null, "Context cannot be null.");
        Preconditions.checkArgument(callback != null, "Callback cannot be null.");

        mContext = context;
        mCallback = callback;

        mVertCarPos = ByteBuffer.allocateDirect(sVertCarPosData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertCarPos.put(sVertCarPosData).position(0);

        double angleInRadian = Math.toRadians(angleInDegree);
        float[] rotated = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
        float sin = (float)Math.sin(angleInRadian);
        float cos = (float)Math.cos(angleInRadian);

        rotated[0] += cos * sVertCarTexData[0] - sin * sVertCarTexData[1];
        rotated[1] += sin * sVertCarTexData[0] + cos * sVertCarTexData[1];
        rotated[2] += cos * sVertCarTexData[2] - sin * sVertCarTexData[3];
        rotated[3] += sin * sVertCarTexData[2] + cos * sVertCarTexData[3];
        rotated[4] += cos * sVertCarTexData[4] - sin * sVertCarTexData[5];
        rotated[5] += sin * sVertCarTexData[4] + cos * sVertCarTexData[5];
        rotated[6] += cos * sVertCarTexData[6] - sin * sVertCarTexData[7];
        rotated[7] += sin * sVertCarTexData[6] + cos * sVertCarTexData[7];

        mVertCarTex = ByteBuffer.allocateDirect(sVertCarTexData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertCarTex.put(rotated).position(0);
    }

    public void clearBuffer() {
        CarEvsBufferDescriptor bufferToReturn = null;
        synchronized (mLock) {
            if (mBufferInUse == null) {
                return;
            }

            bufferToReturn = mBufferInUse;
            mBufferInUse = null;
        }

        // bufferToReturn is not null here.
        mCallback.onBufferProcessed(bufferToReturn);
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        // Use the GLES20 class's static methods instead of a passed GL10 interface.

        CarEvsBufferDescriptor bufferToRender = null;
        CarEvsBufferDescriptor bufferToReturn = null;
        CarEvsBufferDescriptor newFrame = mCallback.onBufferRequested();

        synchronized (mLock) {
            if (newFrame != null) {
                // If a new frame has not been delivered yet, we're using a previous frame.
                if (mBufferInUse != null) {
                    bufferToReturn = mBufferInUse;
                }
                mBufferInUse = newFrame;
            }
            bufferToRender = mBufferInUse;
        }

        if (bufferToRender == null) {
            if (DBG) {
                Log.d(TAG, "No buffer to draw.");
            }
            return;
        }

        if (bufferToReturn != null) {
            mCallback.onBufferProcessed(bufferToReturn);
        }

        // Specify a shader program to use
        GLES20.glUseProgram(mProgram);

        // Set a cameraMat as 4x4 identity matrix
        int matrix = GLES20.glGetUniformLocation(mProgram, "cameraMat");
        if (matrix < 0) {
            throw new RuntimeException("Could not get a attribute location for cameraMat");
        }
        GLES20.glUniformMatrix4fv(matrix, 1, false, sIdentityMatrix, 0);

        // Retrieve a hardware buffer from a descriptor and update the texture
        HardwareBuffer buffer = bufferToRender.getHardwareBuffer();

        // Update the texture with a given hardware buffer
        if (!nUpdateTexture(buffer, mTextureId)) {
            throw new RuntimeException(
                    "Failed to update the texture with the preview frame");
        }

        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        // Select active texture unit
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Bind a named texture to the target
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId);

        // Use a texture slot 0 as the source
        int sampler = GLES20.glGetUniformLocation(mProgram, "tex");
        if (sampler < 0) {
            throw new RuntimeException("Could not get a attribute location for tex");
        }
        GLES20.glUniform1i(sampler, 0);

        // We'll ignore the alpha value
        GLES20.glDisable(GLES20.GL_BLEND);

        // Define an array of generic vertex attribute data
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, mVertCarPos);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 0, mVertCarTex);

        // Enable a generic vertex attribute array
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glEnableVertexAttribArray(1);

        // Render primitives from array data
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(0);
        GLES20.glDisableVertexAttribArray(1);

        // Wait until all GL execution is complete
        GLES20.glFinish();
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        // Use the GLES20 class's static methods instead of a passed GL10 interface.
        GLES20.glViewport(0, 0, width, height);

        mWidth = width;
        mHeight = height;
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        // Use the GLES20 class's static methods instead of a passed GL10 interface.
        mProgram = buildShaderProgram(mVertexShader, mFragmentShader);
        if (mProgram == 0) {
            Log.e(TAG, "Failed to build shader programs");
            return;
        }

        // Generate texture name
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureId = textures[0];
        if (mTextureId <= 0) {
            Log.e(TAG, "Did not get a texture handle");
            return;
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId);
        // Use a linear interpolation to upscale the texture
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        // Use a nearest-neighbor to downscale the texture
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        // Clamp s, t coordinates at the edges
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) {
            Log.e(TAG, "Failed to create a shader for " + source);
            return 0;
        }

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ": ");
            Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    private int buildShaderProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            Log.e(TAG, "Failed to load a vertex shader");
            return 0;
        }

        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            Log.e(TAG, "Failed to load a fragment shader");
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program == 0) {
            Log.e(TAG, "Failed to create a program");
            return 0;
        }

        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, fragmentShader);
        checkGlError("glAttachShader");

        GLES20.glBindAttribLocation(program, 0, "pos");
        GLES20.glBindAttribLocation(program, 1, "tex");

        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link a program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }

        return program;
    }

    private static void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + gluErrorString(error));
        }
    }

    private native boolean nUpdateTexture(HardwareBuffer buffer, int textureId);
}
