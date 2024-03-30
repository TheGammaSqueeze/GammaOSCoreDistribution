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

import com.android.bedstead.harrier.annotations.AnnotationRunPrecedence;
import com.android.bedstead.harrier.annotations.CrossUserTest;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.OtherUser;
import com.android.bedstead.harrier.annotations.PermissionTest;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.RequireRunOnTvProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.UserPair;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.meta.RepeatingAnnotation;
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone;
import com.android.bedstead.nene.annotations.Nullable;
import com.android.bedstead.nene.exceptions.NeneException;

import com.google.auto.value.AutoAnnotation;

import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A JUnit test runner for use with Bedstead.
 */
public final class BedsteadJUnit4 extends BlockJUnit4ClassRunner {

    private static final String BEDSTEAD_PACKAGE_NAME = "com.android.bedstead";

    @AutoAnnotation
    private static EnsureHasPermission ensureHasPermission(String[] value) {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasPermission(value);
    }

    @AutoAnnotation
    private static EnsureDoesNotHavePermission ensureDoesNotHavePermission(String[] value) {
        return new AutoAnnotation_BedsteadJUnit4_ensureDoesNotHavePermission(value);
    }

    @AutoAnnotation
    private static RequireRunOnSystemUser requireRunOnSystemUser() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnSystemUser();
    }

    @AutoAnnotation
    private static RequireRunOnPrimaryUser requireRunOnPrimaryUser() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnPrimaryUser();
    }

    @AutoAnnotation
    private static RequireRunOnSecondaryUser requireRunOnSecondaryUser() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnSecondaryUser();
    }

    @AutoAnnotation
    private static RequireRunOnWorkProfile requireRunOnWorkProfile() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnWorkProfile();
    }

    @AutoAnnotation
    private static RequireRunOnTvProfile requireRunOnTvProfile() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnTvProfile();
    }

    @AutoAnnotation
    private static EnsureHasSecondaryUser ensureHasSecondaryUser() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasSecondaryUser();
    }

    @AutoAnnotation
    private static EnsureHasWorkProfile ensureHasWorkProfile() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasWorkProfile();
    }

    @AutoAnnotation
    private static EnsureHasTvProfile ensureHasTvProfile() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasTvProfile();
    }

    @AutoAnnotation
    private static OtherUser otherUser(UserType value) {
        return new AutoAnnotation_BedsteadJUnit4_otherUser(value);
    }

    // These are annotations which are not included indirectly
    private static final Set<String> sIgnoredAnnotationPackages = new HashSet<>();

    static {
        sIgnoredAnnotationPackages.add("java.lang.annotation");
        sIgnoredAnnotationPackages.add("com.android.bedstead.harrier.annotations.meta");
        sIgnoredAnnotationPackages.add("kotlin.*");
        sIgnoredAnnotationPackages.add("org.junit");
    }

    static int annotationSorter(Annotation a, Annotation b) {
        return getAnnotationWeight(a) - getAnnotationWeight(b);
    }

    private static int getAnnotationWeight(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            // Special case, not important
            return AnnotationRunPrecedence.PRECEDENCE_NOT_IMPORTANT;
        }

        if (!annotation.annotationType().getPackage().getName().startsWith(BEDSTEAD_PACKAGE_NAME)) {
            return AnnotationRunPrecedence.FIRST;
        }

        try {
            return (int) annotation.annotationType().getMethod("weight").invoke(annotation);
        } catch (NoSuchMethodException e) {
            // Default to PRECEDENCE_NOT_IMPORTANT if no weight is found on the annotation.
            return AnnotationRunPrecedence.PRECEDENCE_NOT_IMPORTANT;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NeneException("Failed to invoke weight on this annotation: " + annotation, e);
        }
    }

    static String getParameterName(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return ((DynamicParameterizedAnnotation) annotation).name();
        }
        return annotation.annotationType().getSimpleName();
    }

    /**
     * Resolve annotations recursively.
     *
     * @param parameterizedAnnotation The class of the parameterized annotation to expand, if any
     */
    public static void resolveRecursiveAnnotations(List<Annotation> annotations,
            @Nullable Annotation parameterizedAnnotation) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            annotations.remove(index);
            List<Annotation> replacementAnnotations =
                    getReplacementAnnotations(annotation, parameterizedAnnotation);
            replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);
            annotations.addAll(index, replacementAnnotations);
            index += replacementAnnotations.size();
        }
    }

    private static boolean isParameterizedAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return true;
        }

        return annotation.annotationType().getAnnotation(ParameterizedAnnotation.class) != null;
    }

    private static Annotation[] getIndirectAnnotations(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return ((DynamicParameterizedAnnotation) annotation).annotations();
        }
        return annotation.annotationType().getAnnotations();
    }

    private static boolean isRepeatingAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return false;
        }

        return annotation.annotationType().getAnnotation(RepeatingAnnotation.class) != null;
    }

    private static List<Annotation> getReplacementAnnotations(Annotation annotation,
            @Nullable Annotation parameterizedAnnotation) {
        List<Annotation> replacementAnnotations = new ArrayList<>();

        if (isRepeatingAnnotation(annotation)) {
            try {
                Annotation[] annotations =
                        (Annotation[]) annotation.annotationType()
                                .getMethod("value").invoke(annotation);
                Collections.addAll(replacementAnnotations, annotations);
                return replacementAnnotations;
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new NeneException("Error expanding repeated annotations", e);
            }
        }

        if (isParameterizedAnnotation(annotation) && !annotation.equals(parameterizedAnnotation)) {
            return replacementAnnotations;
        }

        for (Annotation indirectAnnotation : getIndirectAnnotations(annotation)) {
            if (shouldSkipAnnotation(annotation)) {
                continue;
            }

            replacementAnnotations.addAll(getReplacementAnnotations(
                    indirectAnnotation, parameterizedAnnotation));
        }

        if (!(annotation instanceof DynamicParameterizedAnnotation)) {
            // We drop the fake annotation once it's replaced
            replacementAnnotations.add(annotation);
        }

        return replacementAnnotations;
    }

    private static boolean shouldSkipAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return false;
        }

        String annotationPackage = annotation.annotationType().getPackage().getName();

        for (String ignoredPackage : sIgnoredAnnotationPackages) {
            if (ignoredPackage.endsWith(".*")) {
                if (annotationPackage.startsWith(
                        ignoredPackage.substring(0, ignoredPackage.length() - 2))) {
                    return true;
                }
            } else if (annotationPackage.equals(ignoredPackage)) {
                return true;
            }
        }

        return false;
    }

    public BedsteadJUnit4(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    private boolean annotationShouldBeSkipped(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return false;
        }

        return annotation.annotationType().equals(IncludeNone.class);
    }

    private static List<FrameworkMethod> getBasicTests(TestClass testClass) {
        Set<FrameworkMethod> methods = new HashSet<>();

        methods.addAll(testClass.getAnnotatedMethods(Test.class));
        methods.addAll(testClass.getAnnotatedMethods(PolicyAppliesTest.class));
        methods.addAll(testClass.getAnnotatedMethods(PolicyDoesNotApplyTest.class));
        methods.addAll(testClass.getAnnotatedMethods(CanSetPolicyTest.class));
        methods.addAll(testClass.getAnnotatedMethods(CannotSetPolicyTest.class));
        methods.addAll(testClass.getAnnotatedMethods(UserTest.class));
        methods.addAll(testClass.getAnnotatedMethods(CrossUserTest.class));
        methods.addAll(testClass.getAnnotatedMethods(PermissionTest.class));

        return new ArrayList<>(methods);
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        List<FrameworkMethod> basicTests = getBasicTests(getTestClass());
        List<FrameworkMethod> modifiedTests = new ArrayList<>();

        for (FrameworkMethod m : basicTests) {
            Set<Annotation> parameterizedAnnotations = getParameterizedAnnotations(m);

            if (parameterizedAnnotations.isEmpty()) {
                // Unparameterized, just add the original
                modifiedTests.add(new BedsteadFrameworkMethod(m.getMethod()));
            }

            for (Annotation annotation : parameterizedAnnotations) {
                if (annotationShouldBeSkipped(annotation)) {
                    // Special case - does not generate a run
                    continue;
                }
                modifiedTests.add(
                        new BedsteadFrameworkMethod(m.getMethod(), annotation));
            }
        }

        modifiedTests = generateGeneralParameterisationMethods(modifiedTests);

        sortMethodsByBedsteadAnnotations(modifiedTests);

        return modifiedTests;
    }

    private static List<FrameworkMethod> generateGeneralParameterisationMethods(
            List<FrameworkMethod> modifiedTests) {
        return modifiedTests.stream()
                .flatMap(BedsteadJUnit4::generateGeneralParameterisationMethods)
                .collect(Collectors.toList());
    }

    private static Stream<FrameworkMethod> generateGeneralParameterisationMethods(
            FrameworkMethod method) {
        Stream<FrameworkMethod> expandedMethods = Stream.of(method);
        if (method.getMethod().getParameterCount() == 0) {
            return expandedMethods;
        }

        for (Parameter parameter : method.getMethod().getParameters()) {
            List<Annotation> annotations = new ArrayList<>(
                    Arrays.asList(parameter.getAnnotations()));
            resolveRecursiveAnnotations(annotations, /* parameterizedAnnotation= */ null);

            boolean hasParameterised = false;

            for (Annotation annotation : annotations) {
                if (annotation instanceof StringTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    StringTestParameter stringTestParameter = (StringTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyStringTestParameter(i, stringTestParameter));
                } else if (annotation instanceof IntTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    IntTestParameter intTestParameter = (IntTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyIntTestParameter(i, intTestParameter));
                } else if (annotation instanceof EnumTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    EnumTestParameter enumTestParameter = (EnumTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyEnumTestParameter(i, enumTestParameter));
                }
            }

            if (!hasParameterised) {
                throw new IllegalStateException(
                        "Parameter " + parameter + " must be annotated as parameterised");
            }
        }

        return expandedMethods;
    }

    private static Stream<FrameworkMethod> applyStringTestParameter(FrameworkMethod frameworkMethod,
            StringTestParameter stringTestParameter) {
        return Stream.of(stringTestParameter.value()).map(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    private static Stream<FrameworkMethod> applyIntTestParameter(FrameworkMethod frameworkMethod,
            IntTestParameter intTestParameter) {
        return Arrays.stream(intTestParameter.value()).mapToObj(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    private static Stream<FrameworkMethod> applyEnumTestParameter(FrameworkMethod frameworkMethod,
            EnumTestParameter enumTestParameter) {
        return Arrays.stream(enumTestParameter.value().getEnumConstants()).map(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    /**
     * Sort methods so that methods with identical bedstead annotations are together.
     *
     * <p>This will also ensure that all tests methods which are not annotated for bedstead will
     * run before any tests which are annotated.
     */
    private void sortMethodsByBedsteadAnnotations(List<FrameworkMethod> modifiedTests) {
        List<Annotation> bedsteadAnnotationsSortedByMostCommon =
                bedsteadAnnotationsSortedByMostCommon(modifiedTests);

        modifiedTests.sort((o1, o2) -> {
            for (Annotation annotation : bedsteadAnnotationsSortedByMostCommon) {
                boolean o1HasAnnotation = o1.getAnnotation(annotation.annotationType()) != null;
                boolean o2HasAnnotation = o2.getAnnotation(annotation.annotationType()) != null;

                if (o1HasAnnotation && !o2HasAnnotation) {
                    // o1 goes to the end
                    return 1;
                } else if (o2HasAnnotation && !o1HasAnnotation) {
                    return -1;
                }
            }
            return 0;
        });
    }

    private List<Annotation> bedsteadAnnotationsSortedByMostCommon(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCounts = countAnnotations(methods);
        List<Annotation> annotations = new ArrayList<>(annotationCounts.keySet());

        annotations.removeIf(
                annotation ->
                        !annotation.annotationType()
                                .getCanonicalName().contains(BEDSTEAD_PACKAGE_NAME));

        annotations.sort(Comparator.comparingInt(annotationCounts::get));
        Collections.reverse(annotations);

        return annotations;
    }

    private Map<Annotation, Integer> countAnnotations(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCounts = new HashMap<>();

        for (FrameworkMethod method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                annotationCounts.put(
                        annotation, annotationCounts.getOrDefault(annotation, 0) + 1);
            }
        }

        return annotationCounts;
    }

    private Set<Annotation> getParameterizedAnnotations(FrameworkMethod method) {
        Set<Annotation> parameterizedAnnotations = new HashSet<>();
        List<Annotation> annotations = new ArrayList<>(Arrays.asList(method.getAnnotations()));

        parseEnterpriseAnnotations(annotations);
        parsePermissionAnnotations(annotations);
        parseUserAnnotations(annotations);

        for (Annotation annotation : annotations) {
            if (isParameterizedAnnotation(annotation)) {
                parameterizedAnnotations.add(annotation);
            }
        }

        return parameterizedAnnotations;
    }

    /**
     * Parse enterprise-specific annotations.
     *
     * <p>To be used before general annotation processing.
     */
    static void parseEnterpriseAnnotations(List<Annotation> annotations) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            if (annotation instanceof PolicyAppliesTest) {
                annotations.remove(index);
                Class<?> policy = ((PolicyAppliesTest) annotation).policy();

                EnterprisePolicy enterprisePolicy =
                        policy.getAnnotation(EnterprisePolicy.class);
                List<Annotation> replacementAnnotations =
                        Policy.policyAppliesStates(policy.getName(), enterprisePolicy);
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else if (annotation instanceof PolicyDoesNotApplyTest) {
                annotations.remove(index);
                Class<?> policy = ((PolicyDoesNotApplyTest) annotation).policy();

                EnterprisePolicy enterprisePolicy =
                        policy.getAnnotation(EnterprisePolicy.class);
                List<Annotation> replacementAnnotations =
                        Policy.policyDoesNotApplyStates(policy.getName(), enterprisePolicy);
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else if (annotation instanceof CannotSetPolicyTest) {
                annotations.remove(index);
                Class<?> policy = ((CannotSetPolicyTest) annotation).policy();

                EnterprisePolicy enterprisePolicy =
                        policy.getAnnotation(EnterprisePolicy.class);
                List<Annotation> replacementAnnotations =
                        Policy.cannotSetPolicyStates(policy.getName(), enterprisePolicy,
                                ((CannotSetPolicyTest) annotation).includeDeviceAdminStates(),
                                ((CannotSetPolicyTest) annotation).includeNonDeviceAdminStates());
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else if (annotation instanceof CanSetPolicyTest) {
                annotations.remove(index);
                Class<?> policy = ((CanSetPolicyTest) annotation).policy();
                boolean singleTestOnly = ((CanSetPolicyTest) annotation).singleTestOnly();

                EnterprisePolicy enterprisePolicy =
                        policy.getAnnotation(EnterprisePolicy.class);
                List<Annotation> replacementAnnotations =
                        Policy.canSetPolicyStates(
                                policy.getName(), enterprisePolicy, singleTestOnly);
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else {
                index++;
            }
        }
    }

    /**
     * Parse @PermissionTest annotations.
     *
     * <p>To be used before general annotation processing.
     */
    static void parsePermissionAnnotations(List<Annotation> annotations) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            if (annotation instanceof PermissionTest) {
                annotations.remove(index);

                List<Annotation> replacementAnnotations = generatePermissionAnnotations(
                        ((PermissionTest) annotation).value());
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else {
                index++;
            }
        }
    }

    private static List<Annotation> generatePermissionAnnotations(String[] permissions) {
        Set<String> allPermissions = new HashSet<>(Arrays.asList(permissions));
        List<Annotation> replacementAnnotations = new ArrayList<>();

        for (String permission : permissions) {
            allPermissions.remove(permission);
            replacementAnnotations.add(
                    new DynamicParameterizedAnnotation(
                            permission,
                            new Annotation[]{
                                    ensureHasPermission(new String[]{permission}),
                                    ensureDoesNotHavePermission(allPermissions.toArray(new String[]{}))
                            }));
            allPermissions.add(permission);
        }

        return replacementAnnotations;
    }

    /**
     * Parse @UserTest and @CrossUserTest annotations.
     *
     * <p>To be used before general annotation processing.
     */
    static void parseUserAnnotations(List<Annotation> annotations) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            if (annotation instanceof UserTest) {
                annotations.remove(index);

                List<Annotation> replacementAnnotations = generateUserAnnotations(
                        ((UserTest) annotation).value());
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else if (annotation instanceof CrossUserTest) {
                annotations.remove(index);

                CrossUserTest crossUserTestAnnotation = (CrossUserTest) annotation;
                List<Annotation> replacementAnnotations = generateCrossUserAnnotations(
                        crossUserTestAnnotation.value());
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else {
                index++;
            }
        }
    }

    private static List<Annotation> generateUserAnnotations(UserType[] userTypes) {
        List<Annotation> replacementAnnotations = new ArrayList<>();

        for (UserType userType : userTypes) {
            Annotation runOnUserAnnotation = getRunOnAnnotation(userType, "@UserTest");
            replacementAnnotations.add(
                    new DynamicParameterizedAnnotation(
                            userType.name(),
                            new Annotation[]{runOnUserAnnotation}));
        }

        return replacementAnnotations;
    }

    private static List<Annotation> generateCrossUserAnnotations(UserPair[] userPairs) {
        List<Annotation> replacementAnnotations = new ArrayList<>();

        for (UserPair userPair : userPairs) {
            Annotation[] annotations = new Annotation[]{
                    getRunOnAnnotation(userPair.from(), "@CrossUserTest"),
                    otherUser(userPair.to())
            };
            if (userPair.from() != userPair.to()) {
                Annotation hasUserAnnotation =
                        getHasUserAnnotation(userPair.to(), "@CrossUserTest");
                if (hasUserAnnotation != null) {
                    annotations = new Annotation[]{
                            annotations[0],
                            annotations[1],
                            hasUserAnnotation};
                }
            }

            replacementAnnotations.add(
                    new DynamicParameterizedAnnotation(
                            userPair.from().name() + "_to_" + userPair.to().name(),
                            annotations));
        }

        return replacementAnnotations;
    }

    private static Annotation getRunOnAnnotation(UserType userType, String annotationName) {
        switch (userType) {
            case SYSTEM_USER:
                return requireRunOnSystemUser();
            case PRIMARY_USER:
                return requireRunOnPrimaryUser();
            case SECONDARY_USER:
                return requireRunOnSecondaryUser();
            case WORK_PROFILE:
                return requireRunOnWorkProfile();
            case TV_PROFILE:
                return requireRunOnTvProfile();
            default:
                throw new IllegalStateException(
                        "UserType " + userType + " is not compatible with " + annotationName);
        }
    }

    private static Annotation getHasUserAnnotation(UserType userType, String annotationName) {
        switch (userType) {
            case SYSTEM_USER:
                return null; // We always have a system user
            case PRIMARY_USER:
                return null; // We assume we always have a primary user (this may change)
            case SECONDARY_USER:
                return ensureHasSecondaryUser();
            case WORK_PROFILE:
                return ensureHasWorkProfile();
            case TV_PROFILE:
                return ensureHasTvProfile();
            default:
                throw new IllegalStateException(
                        "UserType " + userType + " is not compatible with " + annotationName);
        }
    }

    @Override
    protected List<TestRule> classRules() {
        List<TestRule> rules = super.classRules();

        for (TestRule rule : rules) {
            if (rule instanceof HarrierRule) {
                HarrierRule harrierRule = (HarrierRule) rule;

                harrierRule.setSkipTestTeardown(true);
                harrierRule.setUsingBedsteadJUnit4(true);

                break;
            }
        }

        return rules;
    }

    /**
     * True if the test is running in debug mode.
     *
     * <p>This will result in additional debugging information being added which would otherwise
     * be dropped to improve test performance.
     *
     * <p>To enable this, pass the "bedstead-debug" instrumentation arg as "true"
     */
    public static boolean isDebug() {
        try {
            Class instrumentationRegistryClass = Class.forName(
                        "androidx.test.platform.app.InstrumentationRegistry");

            Object arguments = instrumentationRegistryClass.getMethod("getArguments")
                    .invoke(null);
            return Boolean.parseBoolean((String) arguments.getClass()
                    .getMethod("getString", String.class, String.class)
                    .invoke(arguments, "bedstead-debug", "false"));
        } catch (ClassNotFoundException e) {
            return false; // Must be on the host so can't access debug information
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Error getting isDebug", e);
        }
    }

    @Override
    protected void validateTestMethods(List<Throwable> errors) {
        // We do allow arguments - they will fail validation later on if not properly annotated
    }
}
