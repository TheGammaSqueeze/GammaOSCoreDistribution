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
package android.platform.test.microbenchmark;

import android.platform.test.rule.ArtifactSaver;
import android.platform.test.rule.SamplerRule;

import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runner for functional tests that's compatible with annotations used in microbenchmark
 * tests. @Before/@After are nested inside @NoMetricBefore/@NoMetricAfter. TODO(b/205019000): this
 * class is seen as a temporary solution, and is supposed to be eventually replaced with a permanent
 * one.
 */
public class Functional extends BlockJUnit4ClassRunner {

    private final Set<FrameworkMethod> mMethodsWithSavedArtifacts = new HashSet<>();
    private List<FrameworkMethod> mFilteredChildren;

    public Functional(Class<?> klass) throws InitializationError {
        super(new TestClass(klass));

        mFilteredChildren = getChildren();
    }

    private Statement artifactSaver(Statement statement, Stream<FrameworkMethod> methods) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    statement.evaluate();
                } catch (Throwable e) {
                    methods.forEach(
                            method -> {
                                if (mMethodsWithSavedArtifacts.contains(method)) return;
                                mMethodsWithSavedArtifacts.add(method);
                                ArtifactSaver.onError(describeChild(method), e);
                            });
                    throw e;
                }
            }
        };
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        // Apply the original filtering logic...
        super.filter(filter);
        // ...then re-implement it for local use.
        mFilteredChildren =
                getChildren().stream()
                        .filter(c -> filter.shouldRun(describeChild(c)))
                        .collect(Collectors.toList());
    }

    private List<FrameworkMethod> getFilteredChildren() {
        return mFilteredChildren;
    }

    @Override
    protected Statement withBefores(FrameworkMethod method, Object target, Statement s) {
        s = super.withBefores(method, target, s);

        // Add @NoMetricBefore's
        List<FrameworkMethod> befores =
                getTestClass().getAnnotatedMethods(Microbenchmark.NoMetricBefore.class);
        final Statement statement = befores.isEmpty() ? s : new RunBefores(s, befores, target);
        // Error artifact saver for exceptions thrown in test-befores and the test method, before
        // test-afters and the exit part of test rules are executed.
        return artifactSaver(statement, Stream.of(method));
    }

    @Override
    protected Statement withAfters(FrameworkMethod method, Object target, Statement s) {
        // Add @NoMetricAfter's
        List<FrameworkMethod> afters =
                getTestClass().getAnnotatedMethods(Microbenchmark.NoMetricAfter.class);
        s = afters.isEmpty() ? s : new RunAfters(s, afters, target);

        final Statement statement = super.withAfters(method, target, s);
        // Error artifact saver for exceptions thrown in "method-afters", i.e. outside the method
        // and method-befores, but before the finalizing the rules.
        return artifactSaver(statement, Stream.of(method));
    }

    @Override
    protected Statement methodBlock(FrameworkMethod method) {
        // Error artifact saver for exceptions thrown outside "method-afters", i.e. in method rules.
        return artifactSaver(super.methodBlock(method), Stream.of(method));
    }

    @Override
    protected Statement withBeforeClasses(Statement s) {
        // Error artifact saver for exceptions thrown in class-befores, before class-afters and
        // the exit part of class rules are executed.
        return artifactSaver(super.withBeforeClasses(s), getFilteredChildren().stream());
    }

    @Override
    protected Statement withAfterClasses(Statement s) {
        // Error artifact saver for exceptions thrown outside "class-befores", but inside class
        // rules, i.e. in class afters.
        return artifactSaver(super.withAfterClasses(s), getFilteredChildren().stream());
    }

    @Override
    protected Statement classBlock(RunNotifier notifier) {
        // Error artifact saver for exceptions thrown outside class befores and afters, i.e. in
        // class rules.
        final Statement parentStatement;
        try {
            SamplerRule.enable(true);
            parentStatement = super.classBlock(notifier);
        } finally {
            SamplerRule.enable(false);
        }
        final Statement statement = artifactSaver(parentStatement, getFilteredChildren().stream());
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mMethodsWithSavedArtifacts.clear();

                try {
                    statement.evaluate();
                } catch (Throwable e) {
                    // We get here if class-level (static) @Before/@Afters or rules fail.
                    // To ensure a test output that looks correctly to TF, we need to properly
                    // notify 'notifier' about all test methods.
                    // We'll report that all tests methods failed with the error that comes from
                    // the class block.
                    final List<FrameworkMethod> children = getFilteredChildren();
                    if (children.isEmpty()) {
                        // If there are no test methods, just rethrow.
                        throw e;
                    }

                    for (FrameworkMethod testMethod : children) {
                        final Description description = describeChild(testMethod);
                        notifier.fireTestStarted(description);
                        if (e instanceof AssumptionViolatedException) {
                            notifier.fireTestAssumptionFailed(new Failure(description, e));
                        } else {
                            notifier.fireTestFailure(new Failure(description, e));
                        }
                        notifier.fireTestFinished(description);
                    }
                }
            }
        };
    }
}
