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

import static com.google.common.base.Preconditions.checkState;
import static dagger.hilt.processor.internal.HiltCompilerOptions.isCrossCompilationRootValidationDisabled;
import static dagger.hilt.processor.internal.HiltCompilerOptions.isSharedTestComponentsEnabled;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.Comparator.comparing;
import static javax.lang.model.element.Modifier.PUBLIC;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.aggregateddeps.ComponentDependencies;
import dagger.hilt.processor.internal.definecomponent.DefineComponents;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputs;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Processor that outputs dagger components based on transitive build deps. */
@IncrementalAnnotationProcessor(AGGREGATING)
@AutoService(Processor.class)
public final class RootProcessor extends BaseProcessor {
  private static final Comparator<TypeElement> QUALIFIED_NAME_COMPARATOR =
      comparing(TypeElement::getQualifiedName, (n1, n2) -> n1.toString().compareTo(n2.toString()));

  private final Set<ClassName> processed = new HashSet<>();
  // TODO(bcorso): Consider using a Dagger component to create/scope these objects
  private final DefineComponents defineComponents = DefineComponents.create();
  private GeneratesRootInputs generatesRootInputs;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    generatesRootInputs = new GeneratesRootInputs(processingEnvironment);
  }

  @Override
  public ImmutableSet<String> getSupportedAnnotationTypes() {
    return ImmutableSet.<String>builder()
        .addAll(
            Arrays.stream(RootType.values())
                .map(rootType -> rootType.className().toString())
                .collect(toImmutableSet()))
        .build();
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    TypeElement rootElement = MoreElements.asType(element);
    RootType rootType = RootType.of(rootElement);
    if (rootType.isTestRoot()) {
      new TestInjectorGenerator(
              getProcessingEnv(), TestRootMetadata.of(getProcessingEnv(), rootElement))
          .generate();
    }
    new AggregatedRootGenerator(rootElement, annotation, getProcessingEnv()).generate();
  }

  @Override
  public void postRoundProcess(RoundEnvironment roundEnv) throws Exception {
    Set<Element> newElements = generatesRootInputs.getElementsToWaitFor(roundEnv);
    if (!processed.isEmpty() ) {
      checkState(
          newElements.isEmpty(),
          "Found extra modules after compilation: %s\n"
              + "(If you are adding an annotation processor that generates root input for hilt, "
              + "the annotation must be annotated with @dagger.hilt.GeneratesRootInput.\n)",
          newElements);
    }

    if (!newElements.isEmpty()) {
      // Skip further processing since there's new elements that generate root inputs in this round.
      return;
    }

    ImmutableSet<Root> allRoots =
        AggregatedRootMetadata.from(getElementUtils()).stream()
            .map(metadata -> Root.create(metadata.rootElement(), getProcessingEnv()))
            .collect(toImmutableSet());

    ImmutableSet<Root> processedRoots =
        ProcessedRootSentinelMetadata.from(getElementUtils()).stream()
            .flatMap(metadata -> metadata.rootElements().stream())
            .map(rootElement -> Root.create(rootElement, getProcessingEnv()))
            .collect(toImmutableSet());

    ImmutableSet<Root> rootsToProcess =
        allRoots.stream()
            .filter(root -> !processedRoots.contains(root))
            .filter(root -> !processed.contains(rootName(root)))
            .collect(toImmutableSet());

    if (rootsToProcess.isEmpty()) {
      // Skip further processing since there's no roots that need processing.
      return;
    }

    // TODO(bcorso): Currently, if there's an exception in any of the roots we stop processing
    // all roots. We should consider if it's worth trying to continue processing for other
    // roots. At the moment, I think it's rare that if one root failed the others would not.
    try {
      validateRoots(allRoots, rootsToProcess);

      boolean isTestEnv = rootsToProcess.stream().anyMatch(Root::isTestRoot);
      ComponentNames componentNames =
          isTestEnv && isSharedTestComponentsEnabled(getProcessingEnv())
              ? ComponentNames.withRenamingIntoPackage(
                  ClassNames.DEFAULT_ROOT.packageName(),
                  rootsToProcess.stream().map(Root::element).collect(toImmutableList()))
              : ComponentNames.withoutRenaming();

      ImmutableSet<ComponentDescriptor> componentDescriptors =
          defineComponents.getComponentDescriptors(getElementUtils());
      ComponentTree tree = ComponentTree.from(componentDescriptors);
      ComponentDependencies deps =
          ComponentDependencies.from(componentDescriptors, getElementUtils());
      ImmutableList<RootMetadata> rootMetadatas =
          rootsToProcess.stream()
              .map(root -> RootMetadata.create(root, tree, deps, getProcessingEnv()))
              .collect(toImmutableList());

      for (RootMetadata rootMetadata : rootMetadatas) {
        if (!rootMetadata.canShareTestComponents()) {
          generateComponents(rootMetadata, componentNames);
        }
      }

      if (isTestEnv) {
        ImmutableList<RootMetadata> rootsThatCanShareComponents =
            rootMetadatas.stream()
                .filter(RootMetadata::canShareTestComponents)
                .collect(toImmutableList());
        generateTestComponentData(rootMetadatas, componentNames);
        if (deps.hasEarlyEntryPoints() || !rootsThatCanShareComponents.isEmpty()) {
          Root defaultRoot = Root.createDefaultRoot(getProcessingEnv());
          generateComponents(
              RootMetadata.createForDefaultRoot(
                  defaultRoot, rootsThatCanShareComponents, tree, deps, getProcessingEnv()),
              componentNames);
          EarlySingletonComponentCreatorGenerator.generate(getProcessingEnv());
        }
      }
    } catch (Exception e) {
      for (Root root : rootsToProcess) {
        processed.add(rootName(root));
      }
      throw e;
    } finally {
      rootsToProcess.forEach(this::setProcessingState);
      // Calculate the roots processed in this round. We do this in the finally-block rather than in
      // the try-block because the catch-block can change the processing state.
      ImmutableSet<Root> rootsProcessedInRound =
          rootsToProcess.stream()
              // Only add a sentinel for processed roots. Skip preprocessed roots since those will
              // will be processed in the next round.
              .filter(root -> processed.contains(rootName(root)))
              .collect(toImmutableSet());
      for (Root root : rootsProcessedInRound) {
        new ProcessedRootSentinelGenerator(rootElement(root), getProcessingEnv()).generate();
      }
    }
  }

  private void validateRoots(ImmutableSet<Root> allRoots, ImmutableSet<Root> rootsToProcess) {

    ImmutableSet<TypeElement> rootElementsToProcess =
        rootsToProcess.stream()
            .map(Root::element)
            .sorted(QUALIFIED_NAME_COMPARATOR)
            .collect(toImmutableSet());

    ImmutableSet<TypeElement> appRootElementsToProcess =
        rootsToProcess.stream()
            .filter(root -> !root.isTestRoot())
            .map(Root::element)
            .sorted(QUALIFIED_NAME_COMPARATOR)
            .collect(toImmutableSet());

    // Perform validation between roots in this compilation unit.
    if (!appRootElementsToProcess.isEmpty()) {
      ImmutableSet<TypeElement> testRootElementsToProcess =
          rootsToProcess.stream()
              .filter(Root::isTestRoot)
              .map(Root::element)
              .sorted(QUALIFIED_NAME_COMPARATOR)
              .collect(toImmutableSet());

      ProcessorErrors.checkState(
          testRootElementsToProcess.isEmpty(),
          "Cannot process test roots and app roots in the same compilation unit:"
              + "\n\tApp root in this compilation unit: %s"
              + "\n\tTest roots in this compilation unit: %s",
          appRootElementsToProcess,
          testRootElementsToProcess);

      ProcessorErrors.checkState(
          appRootElementsToProcess.size() == 1,
          "Cannot process multiple app roots in the same compilation unit: %s",
          appRootElementsToProcess);
    }

    // Perform validation across roots previous compilation units.
    if (!isCrossCompilationRootValidationDisabled(rootElementsToProcess, getProcessingEnv())) {
      ImmutableSet<TypeElement> processedTestRootElements =
          allRoots.stream()
              .filter(Root::isTestRoot)
              .filter(root -> !rootsToProcess.contains(root))
              .map(Root::element)
              .sorted(QUALIFIED_NAME_COMPARATOR)
              .collect(toImmutableSet());

      // TODO(b/185742783): Add an explanation or link to docs to explain why we're forbidding this.
      ProcessorErrors.checkState(
          processedTestRootElements.isEmpty(),
          "Cannot process new roots when there are test roots from a previous compilation unit:"
              + "\n\tTest roots from previous compilation unit: %s"
              + "\n\tAll roots from this compilation unit: %s",
          processedTestRootElements,
          rootElementsToProcess);

      ImmutableSet<TypeElement> processedAppRootElements =
          allRoots.stream()
              .filter(root -> !root.isTestRoot())
              .filter(root -> !rootsToProcess.contains(root))
              .map(Root::element)
              .sorted(QUALIFIED_NAME_COMPARATOR)
              .collect(toImmutableSet());

      ProcessorErrors.checkState(
          processedAppRootElements.isEmpty() || appRootElementsToProcess.isEmpty(),
          "Cannot process app roots in this compilation unit since there are app roots in a "
              + "previous compilation unit:"
              + "\n\tApp roots in previous compilation unit: %s"
              + "\n\tApp roots in this compilation unit: %s",
          processedAppRootElements,
          appRootElementsToProcess);
    }
  }

  private void setProcessingState(Root root) {
    processed.add(rootName(root));
  }

  private ClassName rootName(Root root) {
    return ClassName.get(rootElement(root));
  }

  private TypeElement rootElement(Root root) {
    return root.element();
  }

  private void generateComponents(RootMetadata rootMetadata, ComponentNames componentNames)
      throws IOException {
    RootGenerator.generate(rootMetadata, componentNames, getProcessingEnv());
  }

  private void generateTestComponentData(
      ImmutableList<RootMetadata> rootMetadatas, ComponentNames componentNames) throws IOException {
    for (RootMetadata rootMetadata : rootMetadatas) {
      // TODO(bcorso): Consider moving this check earlier into processEach.
      TypeElement testElement = rootMetadata.testRootMetadata().testElement();
      ProcessorErrors.checkState(
          testElement.getModifiers().contains(PUBLIC),
          testElement,
          "Hilt tests must be public, but found: %s",
          testElement);
      new TestComponentDataGenerator(getProcessingEnv(), rootMetadata, componentNames).generate();
    }
  }
}
