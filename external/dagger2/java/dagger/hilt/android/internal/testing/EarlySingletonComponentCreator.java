/*
 * Copyright (C) 2021 The Dagger Authors.
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

/** Creates a test's early component. */
public abstract class EarlySingletonComponentCreator {
  private static final String EARLY_SINGLETON_COMPONENT_CREATOR_IMPL =
      "dagger.hilt.android.internal.testing.EarlySingletonComponentCreatorImpl";

  static Object createComponent() {
    try {
      return Class.forName(EARLY_SINGLETON_COMPONENT_CREATOR_IMPL)
          .asSubclass(EarlySingletonComponentCreator.class)
          .getDeclaredConstructor()
          .newInstance()
          .create();
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InstantiationException
        | InvocationTargetException e) {
      throw new RuntimeException(
          "The EarlyComponent was requested but does not exist. Check that you have annotated "
              + "your test class with @HiltAndroidTest and that the processor is running over your "
              + "test.",
          e);
    }
  }

  /** Creates the early test component. */
  abstract Object create();
}
