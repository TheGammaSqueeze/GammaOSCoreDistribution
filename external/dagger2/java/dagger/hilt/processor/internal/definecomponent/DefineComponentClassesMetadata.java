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

package dagger.hilt.processor.internal.definecomponent;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.AggregatedElements;
import dagger.hilt.processor.internal.AnnotationValues;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * A class that represents the values stored in an {@link
 * dagger.hilt.internal.definecomponent.DefineComponentClasses} annotation.
 */
@AutoValue
abstract class DefineComponentClassesMetadata {

  /**
   * Returns the element annotated with {@code dagger.hilt.internal.definecomponent.DefineComponent}
   * or {@code dagger.hilt.internal.definecomponent.DefineComponent.Builder}.
   */
  abstract TypeElement element();

  /** Returns {@code true} if this element represents a component. */
  abstract boolean isComponent();

  /** Returns {@code true} if this element represents a component builder. */
  boolean isComponentBuilder() {
    return !isComponent();
  }

  static ImmutableSet<DefineComponentClassesMetadata> from(Elements elements) {
    return AggregatedElements.from(
            ClassNames.DEFINE_COMPONENT_CLASSES_PACKAGE,
            ClassNames.DEFINE_COMPONENT_CLASSES,
            elements)
        .stream()
        .map(aggregatedElement -> create(aggregatedElement, elements))
        .collect(toImmutableSet());
  }

  private static DefineComponentClassesMetadata create(TypeElement element, Elements elements) {
    AnnotationMirror annotationMirror =
        Processors.getAnnotationMirror(element, ClassNames.DEFINE_COMPONENT_CLASSES);

    ImmutableMap<String, AnnotationValue> values =
        Processors.getAnnotationValues(elements, annotationMirror);

    String componentName = AnnotationValues.getString(values.get("component"));
    String builderName = AnnotationValues.getString(values.get("builder"));

    ProcessorErrors.checkState(
        !(componentName.isEmpty() && builderName.isEmpty()),
        element,
        "@DefineComponentClasses missing both `component` and `builder` members.");

    ProcessorErrors.checkState(
        componentName.isEmpty() || builderName.isEmpty(),
        element,
        "@DefineComponentClasses should not include both `component` and `builder` members.");

    boolean isComponent = !componentName.isEmpty();
    String componentOrBuilderName = isComponent ? componentName : builderName;
    TypeElement componentOrBuilderElement = elements.getTypeElement(componentOrBuilderName);
    ProcessorErrors.checkState(
        componentOrBuilderElement != null,
        componentOrBuilderElement,
        "%s.%s(), has invalid value: `%s`.",
        ClassNames.DEFINE_COMPONENT_CLASSES.simpleName(),
        isComponent ? "component" : "builder",
        componentOrBuilderName);
    return new AutoValue_DefineComponentClassesMetadata(componentOrBuilderElement, isComponent);
  }
}
