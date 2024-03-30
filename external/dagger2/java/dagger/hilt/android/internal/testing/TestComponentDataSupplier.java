/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.internal.testing;

import java.lang.reflect.InvocationTargetException;

/** Stores the {@link TestComponentData} for a Hilt test class. */
public abstract class TestComponentDataSupplier {

  /** Returns a {@link TestComponentData}. */
  protected abstract TestComponentData get();

  static TestComponentData get(Class<?> testClass) {
    String generatedClassName = getEnclosedClassName(testClass) + "_TestComponentDataSupplier";
    try {
      return Class.forName(generatedClassName)
          .asSubclass(TestComponentDataSupplier.class)
          .getDeclaredConstructor()
          .newInstance()
          .get();
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InstantiationException
        | InvocationTargetException e) {
      throw new RuntimeException(
          String.format(
              "Hilt test, %s, is missing generated file: %s. Check that the test class is "
                  + " annotated with @HiltAndroidTest and that the processor is running over your"
                  + " test.",
              testClass.getSimpleName(),
              generatedClassName),
          e);
    }
  }

  private static String getEnclosedClassName(Class<?> testClass) {
    StringBuilder sb = new StringBuilder();
    Class<?> currClass = testClass;
    while (currClass != null) {
      Class<?> enclosingClass = currClass.getEnclosingClass();
      if (enclosingClass != null) {
        sb.insert(0, "_" + currClass.getSimpleName());
      } else {
        sb.insert(0, currClass.getCanonicalName());
      }
      currClass = enclosingClass;
    }
    return sb.toString();
  }
}
