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

package com.android.bedstead.harrier;

import com.google.common.base.Objects;

import org.junit.runners.model.FrameworkMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * {@link FrameworkMethod} subclass which allows modifying the test name and annotations.
 */
public final class BedsteadFrameworkMethod extends FrameworkMethod {

    private final Annotation mParameterizedAnnotation;
    private final Map<Class<? extends Annotation>, Annotation> mAnnotationsMap =
            new HashMap<>();
    private Annotation[] mAnnotations;

    public BedsteadFrameworkMethod(Method method) {
        this(method, /* parameterizedAnnotation= */ null);
    }

    public BedsteadFrameworkMethod(Method method,
            @Nullable Annotation parameterizedAnnotation) {
        super(method);
        mParameterizedAnnotation = parameterizedAnnotation;

        calculateAnnotations();
    }

    private void calculateAnnotations() {
        List<Annotation> annotations =
                new ArrayList<>(Arrays.asList(getDeclaringClass().getAnnotations()));
        annotations.sort(BedsteadJUnit4::annotationSorter);

        annotations.addAll(Arrays.stream(getMethod().getAnnotations())
                .sorted(BedsteadJUnit4::annotationSorter)
                .collect(Collectors.toList()));

        BedsteadJUnit4.parseEnterpriseAnnotations(annotations);
        BedsteadJUnit4.parsePermissionAnnotations(annotations);
        BedsteadJUnit4.parseUserAnnotations(annotations);

        BedsteadJUnit4.resolveRecursiveAnnotations(annotations, mParameterizedAnnotation);

        mAnnotations = annotations.toArray(new Annotation[0]);
        for (Annotation annotation : annotations) {
            if (annotation instanceof DynamicParameterizedAnnotation) {
                continue; // don't return this
            }
            mAnnotationsMap.put(annotation.annotationType(), annotation);
        }
    }

    @Override
    public String getName() {
        if (mParameterizedAnnotation == null) {
            return super.getName();
        }
        return super.getName() + "[" + BedsteadJUnit4.getParameterName(mParameterizedAnnotation)
                + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        if (!(obj instanceof BedsteadFrameworkMethod)) {
            return false;
        }

        BedsteadFrameworkMethod other = (BedsteadFrameworkMethod) obj;

        return Objects.equal(mParameterizedAnnotation, other.mParameterizedAnnotation);
    }

    @Override
    public Annotation[] getAnnotations() {
        return mAnnotations;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return (T) mAnnotationsMap.get(annotationType);
    }
}
