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

package com.android.systemui.car.displayarea;

import android.view.animation.Interpolator;

/**
 * Animation interpolator which can do time based interpolation over cubic bezier curves as defined
 * by 2 x,y control points.
 *
 * To be clear, this is not just interpolating over the parametric form of B(t) = {x,y}, but over
 * f(x) = y, where x is linear time {0...1} and y is adjusted time {0...1}. We essentially want to
 * do the following:
 * - given that we have a a 2d bezier form of B(t) = {x, y} with 1d forms of bx(t) = x and by(t) = y
 * - compute y in f(x) = y such that at a given xn we have B(tn) = {xn, yn}
 * - where f(x) = by(bx'(x)) where bx'(x) = t is the inverse of bx(t) = x
 *
 * There is no closed form solution to the equation in solving f(x) = y. Presently it uses Newton's
 * method to find t for which B(t) = x, then compute y based on that t.
 *
 * We pregenerate a number of high precision approximations of x-to-t lookup buckets, which we use
 * as the starting guesess for a lower precision look up at runtime. In combination this gives about
 * a 0.1% error margin, which is more than sufficient for the animation interpolations. If we need
 * more precision or more perf we can expand the look up buckets, or use a binary search to drill
 * into it more, or a few other methods.
 *
 * This class is largely a copy of GMM's CubicBezierInterpolator, but has modified constructors and
 * static fields in order to make it easier to input animation specifications from our UX team.
 * See go/cubicbezierinterpolator for the code that was copied.
 */
public final class CarCubicBezierInterpolator implements Interpolator {

    private static final int BUCKET_COUNT = 25;
    private static final int PRECOMPUTE_ITERATIONS = 10;
    private static final int INTERPOLATE_ITERATIONS = 3;
    private static final float EPSILON = 0.0001f;

    private final CarBezierFunction mXFunction;
    private final CarBezierFunction mYfunction;

    // Quick bucketing of X-to-t
    public final float[] mXt = new float[BUCKET_COUNT];

    /**
     * A cubic bezier 2D interpolator, with the bezier curve defined by {P1/C1, P2/C2}.
     */
    public CarCubicBezierInterpolator(float x1, float y1, float x2, float y2) {
        mXFunction = new CarBezierFunction(0, x1, x2, 1);
        mYfunction = new CarBezierFunction(0, y1, y2, 1);

        for (int i = 0; i < mXt.length; i++) {
            float x = (float) i / (float) (mXt.length - 1);
            float t = inverseBezierValue(x, PRECOMPUTE_ITERATIONS, false);
            mXt[i] = t;
        }
    }

    private float inverseBezierValue(
            CarCubicBezierInterpolator this,
            float x,
            int maxIterations,
            boolean useLookup) {
        // Use Newton Raphson Iteration to approximate the T at which Bezier(T) = x.
        int index = (int) (x * (mXt.length - 1));
        float t = useLookup ? mXt[index] : x;
        float tmax = useLookup && (index < mXt.length - 1) ? mXt[index + 1] : 1;

        for (int i = 0; i < maxIterations; ++i) {
            float error = (float) mXFunction.getValue(t) - x;
            if (Math.abs(error) <= EPSILON) {
                return t;
            }
            float slope = (float) mXFunction.getDerivative(t);
            if (slope != 0) {
                t -= error / slope;
            } else {
                // If we landed on a zero-slope we need shift the t in some direction. Shift a
                // bit towards
                // the max.
                t += ((tmax - t) / 2);
            }
        }

        return t;
    }

    @Override
    public float getInterpolation(float time) {
        // The x-axis is linear time. To get the adjusted time we need to find at which T B(T) =
        // time,
        // then compute the y value at T, which represents our adjusted time.
        return (float) mYfunction.getValue(inverseBezierValue(time, INTERPOLATE_ITERATIONS, true));
    }
}
