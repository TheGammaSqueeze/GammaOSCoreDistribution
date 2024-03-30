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

package android.car.test.util;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.annotation.AddedInOrBefore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;


// TODO(b/237565347): Refactor this class so that 'field' and 'method' code is not repeated.
public class AnnotationHelper {

    public static void checkForAnnotation(String[] classes, Class... annotationClasses)
            throws Exception {
        List<String> errorsNoAnnotation = new ArrayList<>();
        List<String> errorsExtraAnnotation = new ArrayList<>();

        for (int i = 0; i < classes.length; i++) {
            String className = classes[i];
            Field[] fields = Class.forName(className).getDeclaredFields();
            for (int j = 0; j < fields.length; j++) {
                Field field = fields[j];
                boolean isAnnotated = containsAddedInAnnotation(field, annotationClasses);
                boolean isPrivate = Modifier.isPrivate(field.getModifiers());

                if (isPrivate && isAnnotated) {
                    errorsExtraAnnotation.add(className + " FIELD: " + field.getName());
                }

                if (!isPrivate && !isAnnotated) {
                    errorsNoAnnotation.add(className + " FIELD: " + field.getName());
                }
            }

            Method[] methods = Class.forName(className).getDeclaredMethods();
            for (int j = 0; j < methods.length; j++) {
                Method method = methods[j];

                // These are some internal methods
                if (method.getName().contains("$")) continue;

                boolean isAnnotated = containsAddedInAnnotation(method, annotationClasses);
                boolean isPrivate = Modifier.isPrivate(method.getModifiers());

                if (isPrivate && isAnnotated) {
                    errorsExtraAnnotation.add(className + " METHOD: " + method.getName());
                }

                if (!isPrivate && !isAnnotated) {
                    errorsNoAnnotation.add(className + " METHOD: " + method.getName());
                }
            }
        }

        StringBuilder errorFlatten = new StringBuilder();
        if (!errorsNoAnnotation.isEmpty()) {
            // TODO(b/240343308): remove @AddedIn once all usages have been replaced
            errorFlatten.append("Errors:\nMissing ApiRequirements (or AddedIn) annotation for-\n");
            errorFlatten.append(String.join("\n", errorsNoAnnotation));
        }

        if (!errorsExtraAnnotation.isEmpty()) {
            // TODO(b/240343308): remove @AddedIn once all usages have been replaced
            errorFlatten.append("\nErrors:\nApiRequirements (or AddedIn) annotation used for "
                    + "private members/methods-\n");
            errorFlatten.append(String.join("\n", errorsExtraAnnotation));
        }

        assertWithMessage(errorFlatten.toString())
                .that(errorsExtraAnnotation.size() + errorsNoAnnotation.size()).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    private static boolean containsAddedInAnnotation(Field field, Class... annotationClasses) {
        for (int i = 0; i < annotationClasses.length; i++) {
            if (field.getAnnotation(annotationClasses[i]) != null) {
                validatedAddInOrBeforeAnnotation(field);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean containsAddedInAnnotation(Method method, Class... annotationClasses) {
        for (int i = 0; i < annotationClasses.length; i++) {
            if (method.getAnnotation(annotationClasses[i]) != null) {
                validatedAddInOrBeforeAnnotation(method);
                return true;
            }
        }
        return false;
    }

    private static void validatedAddInOrBeforeAnnotation(Field field) {
        AddedInOrBefore annotation = field.getAnnotation(AddedInOrBefore.class);
        if (annotation != null) {
            assertWithMessage(field.getDeclaringClass() + ", field:" + field.getName()
                    + " should not use AddedInOrBefore annotation. The annotation was reserved only"
                    + " for APIs added in or before majorVersion:33, minorVersion:0")
                            .that(annotation.majorVersion()).isEqualTo(33);
            assertWithMessage(field.getDeclaringClass() + ", field:" + field.getName()
                    + " should not use AddedInOrBefore annotation. The annotation was reserved only"
                    + " for APIs added in or before majorVersion:33, minorVersion:0")
                    .that(annotation.minorVersion()).isEqualTo(0);
        }
    }

    private static void validatedAddInOrBeforeAnnotation(Method method) {
        AddedInOrBefore annotation = method.getAnnotation(AddedInOrBefore.class);
        if (annotation != null) {
            assertWithMessage(method.getDeclaringClass() + ", method:" + method.getName()
                    + " should not use AddedInOrBefore annotation. The annotation was reserved only"
                    + " for APIs added in or before majorVersion:33, minorVersion:0")
                            .that(annotation.majorVersion()).isEqualTo(33);
            assertWithMessage(method.getDeclaringClass() + ", method:" + method.getName()
                    + " should not use AddedInOrBefore annotation. The annotation was reserved only"
                    + " for APIs added in or before majorVersion:33, minorVersion:0")
                            .that(annotation.minorVersion()).isEqualTo(0);
        }
    }
}
