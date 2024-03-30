/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.root;

import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Metadata for a root element that can trigger the {@link RootProcessor}. */
@AutoValue
abstract class Root {
  /**
   * Creates the default root for this (test) build compilation.
   *
   * <p>A default root installs only the global {@code InstallIn} and {@code TestInstallIn}
   * dependencies. Test-specific dependencies are not installed in the default root.
   *
   * <p>The default root is used for two purposes:
   *
   * <ul>
   *   <li>To inject {@code EarlyEntryPoint} annotated interfaces.
   *   <li>To inject tests that only depend on global dependencies
   * </ul>
   */
  static Root createDefaultRoot(ProcessingEnvironment env) {
    TypeElement rootElement =
        env.getElementUtils().getTypeElement(ClassNames.DEFAULT_ROOT.canonicalName());
    return new AutoValue_Root(rootElement, /*isTestRoot=*/ true);
  }

  /** Creates a {@plainlink Root root} for the given {@plainlink Element element}. */
  static Root create(Element element, ProcessingEnvironment env) {
    TypeElement rootElement = MoreElements.asType(element);
    return new AutoValue_Root(rootElement, RootType.of(rootElement).isTestRoot());
  }

  /** Returns the root element that should be used with processing. */
  abstract TypeElement element();

  /** Returns {@code true} if this is a test root. */
  abstract boolean isTestRoot();

  /** Returns the class name of the root element. */
  ClassName classname() {
    return ClassName.get(element());
  }

  @Override
  public final String toString() {
    return element().toString();
  }

  /** Returns {@code true} if this uses the default root. */
  boolean isDefaultRoot() {
    return classname().equals(ClassNames.DEFAULT_ROOT);
  }
}
