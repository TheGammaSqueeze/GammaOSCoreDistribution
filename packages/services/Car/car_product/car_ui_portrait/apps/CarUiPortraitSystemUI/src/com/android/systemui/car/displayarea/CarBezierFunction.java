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

/**
 * Represents a cubic bezier curve in one dimension. Returns out-of-bounds t values using a straight
 * line instead of continuing the curve into infinity, because this is useful for bezier animations,
 * and beziers for drawing are rarely allowed to use these values.
 * <p>
 * For reference material on bezier curves, try http://pomax.github.io/bezierinfo/
 */
public final class CarBezierFunction {
    // Control points.
    private final double mA;
    private final double mB;
    private final double mC;
    private final double mD;

    /**
     * Constructs bezier curve with the given control points.
     */
    CarBezierFunction(double a, double b, double c, double d) {
        this.mA = a;
        this.mB = b;
        this.mC = c;
        this.mD = d;
    }

    /**
     * Constructs bezier curve that's just a solitary point. This is a degenerate curve when trying
     * to
     * draw using a bezier curve, but useful when using bezier curves as animations.
     */
    CarBezierFunction(double value) {
        mA = mB = mC = mD = value;
    }

    /**
     * Gets the bezier value at given t for the bezier defined by control points {@code a, b, c, d}.
     * The bezier function is defined for {@code t} in [0, 1], and the area outside this range is
     * replaced with a linear function.
     */
    public static double getValue(double t, double a, double b, double c, double d) {
        // Treat outside bounds as linear.
        if (t > 1) {
            return d + getDerivative(1, a, b, c, d) * (t - 1);
        }
        if (t < 0) {
            return a + getDerivative(0, a, b, c, d) * t;
        }
        // Normal bezier equation.
        return cube(1 - t) * a + 3 * square(1 - t) * t * b + 3 * (1 - t) * square(t) * c + cube(t)
                * d;
    }

    /**
     * Gets the bezier value at given {@code t}. The bezier function is defined for {@code t} in
     * [0, 1], and the area outside this range is replaced with a linear function.
     */
    public double getValue(double t) {
        return getValue(t, mA, mB, mC, mD);
    }

    /**
     * Gets the derivative of the bezier value at given {@code t}. Since {@link #getValue} becomes
     * linear outside the range [0, 1], the derivative outside this range is constant.
     */
    public static double getDerivative(double t, double a, double b, double c, double d) {
        // Clamp to [0, 1] to make values linear outside this range.
        t = Math.max(0, t);
        t = Math.min(1, t);
        return 3 * square(1 - t) * (b - a) + 3 * 2 * (1 - t) * t * (c - b) + 3 * square(t) * (d
                - c);
    }

    /**
     * Gets derivative of bezier value at given t for the bezier defined by control points a, b, c,
     * d.
     * Since {@link #getValue} becomes linear outside the range [0, 1], the derivative outside this
     * range is constant.
     */
    public double getDerivative(double t) {
        return getDerivative(t, mA, mB, mC, mD);
    }

    /**
     * Squares x. Math.pow() is significantly slower than multiplying directly for small integer
     * exponents.
     */
    private static double square(double x) {
        return x * x;
    }

    /**
     * Cubes x. Math.pow() is significantly slower than multiplying directly for small integer
     * exponents.
     */
    private static double cube(double x) {
        return x * x * x;
    }
}
