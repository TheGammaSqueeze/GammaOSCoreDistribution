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

import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PARENT_INSTANCE_OF_PROFILE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_IN_BACKGROUND;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_AFFILIATED_OTHER_USERS;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_PARENT;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.APPLIES_TO_UNAFFILIATED_OTHER_USERS;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.CAN_BE_DELEGATED;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.DO_NOT_APPLY_TO_POLICY_DOES_NOT_APPLY_TESTS;
import static com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.NO;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_APP_RESTRICTIONS;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_BLOCK_UNINSTALL;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_CERT_INSTALL;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_CERT_SELECTION;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_ENABLE_SYSTEM_APP;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_INSTALL_EXISTING_PACKAGE;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_KEEP_UNINSTALLED_PACKAGES;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_NETWORK_LOGGING;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_PACKAGE_ACCESS;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_PERMISSION_GRANT;
import static com.android.bedstead.nene.devicepolicy.CommonDevicePolicy.DELEGATION_SECURITY_LOGGING;

import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDelegate;
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;
import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy.AppOp;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnAffiliatedDeviceOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnAffiliatedProfileOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnBackgroundDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfCorporateOwnedProfileOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwnerUsingParentInstance;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerPrimaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerProfileWithNoDeviceOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnUnaffiliatedDeviceOwnerSecondaryUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnUnaffiliatedProfileOwnerSecondaryUser;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for enterprise policy tests.
 */
public final class Policy {

    // TODO(b/219750042): If we leave over appops and permissions then the delegate will have them
    private static final String DELEGATE_PACKAGE_NAME = "com.android.Delegate";

    // Delegate scopes to be used for a "CannotSet" state. All delegate scopes except the ones which
    // should allow use of the API will be granted
    private static final ImmutableSet<String> ALL_DELEGATE_SCOPES = ImmutableSet.of(
            DELEGATION_CERT_INSTALL,
            DELEGATION_APP_RESTRICTIONS,
            DELEGATION_BLOCK_UNINSTALL,
            DELEGATION_PERMISSION_GRANT,
            DELEGATION_PACKAGE_ACCESS,
            DELEGATION_ENABLE_SYSTEM_APP,
            DELEGATION_INSTALL_EXISTING_PACKAGE,
            DELEGATION_KEEP_UNINSTALLED_PACKAGES,
            DELEGATION_NETWORK_LOGGING,
            DELEGATION_CERT_SELECTION,
            DELEGATION_SECURITY_LOGGING
    );

    // This is a map containing all Include* annotations and the flags which lead to them
    // This is not validated - every state must have a single APPLIED_BY annotation
    private static final ImmutableMap<Integer, Function<EnterprisePolicy, Set<Annotation>>>
            STATE_ANNOTATIONS =
            ImmutableMap.<Integer, Function<EnterprisePolicy, Set<Annotation>>>builder()
                    .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnDeviceOwnerUser()))
                    .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnDeviceOwnerUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER | APPLIES_IN_BACKGROUND, singleAnnotation(includeRunOnBackgroundDeviceOwnerUser()))
                    .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER | APPLIES_IN_BACKGROUND | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnBackgroundDeviceOwnerUser(), /* isPrimary= */ true))

                    .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_UNAFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnNonAffiliatedDeviceOwnerSecondaryUser()))
                    .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_UNAFFILIATED_OTHER_USERS | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnNonAffiliatedDeviceOwnerSecondaryUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_AFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnAffiliatedDeviceOwnerSecondaryUser()))
                    .put(APPLIED_BY_DEVICE_OWNER | APPLIES_TO_AFFILIATED_OTHER_USERS | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnAffiliatedDeviceOwnerSecondaryUser(), /* isPrimary= */ true))

                    .put(APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnAffiliatedProfileOwnerSecondaryUser()))
                    .put(APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnAffiliatedProfileOwnerSecondaryUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnUnaffiliatedProfileOwnerSecondaryUser()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnUnaffiliatedProfileOwnerSecondaryUser(), /* isPrimary= */ true))
                    .put(APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnProfileOwnerPrimaryUser()))
                    .put(APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnProfileOwnerPrimaryUser(), /* isPrimary= */ true))

                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnProfileOwnerProfileWithNoDeviceOwner()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnProfileOwnerProfileWithNoDeviceOwner(), /* isPrimary= */ true))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT, singleAnnotation(includeRunOnParentOfProfileOwnerWithNoDeviceOwner()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_PARENT | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnParentOfProfileOwnerWithNoDeviceOwner(), /* isPrimary= */ true))

                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_UNAFFILIATED_OTHER_USERS, singleAnnotation(includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE | APPLIES_TO_UNAFFILIATED_OTHER_USERS | CAN_BE_DELEGATED, generateDelegateAnnotation(includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile(), /* isPrimary= */ true))

                    .put(APPLIED_BY_PARENT_INSTANCE_OF_PROFILE_OWNER | APPLIES_TO_OWN_USER, singleAnnotation(includeRunOnParentOfProfileOwnerUsingParentInstance()))

                    .build();
    // This must contain one key for every APPLIED_BY that is being used, and maps to the
    // "default" for testing that DPC type
    // in general this will be a state which runs on the same user as the dpc.
    private static final ImmutableMap<Integer, Function<EnterprisePolicy, Set<Annotation>>>
            DPC_STATE_ANNOTATIONS_BASE =
            ImmutableMap.<Integer, Function<EnterprisePolicy, Set<Annotation>>>builder()
                    .put(APPLIED_BY_DEVICE_OWNER, (flags) -> hasFlag(flags.dpc(), APPLIED_BY_DEVICE_OWNER | APPLIES_IN_BACKGROUND) ? ImmutableSet.of(includeRunOnBackgroundDeviceOwnerUser()) : ImmutableSet.of(includeRunOnDeviceOwnerUser()))
                    .put(APPLIED_BY_AFFILIATED_PROFILE_OWNER, singleAnnotation(includeRunOnAffiliatedProfileOwnerSecondaryUser()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER, singleAnnotation(includeRunOnProfileOwnerPrimaryUser()))
                    .put(APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO, singleAnnotation(includeRunOnProfileOwnerPrimaryUser()))
                    .put(APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE, singleAnnotation(includeRunOnProfileOwnerProfileWithNoDeviceOwner()))
                    .build();
    private static final Map<Integer, Function<EnterprisePolicy, Set<Annotation>>>
            DPC_STATE_ANNOTATIONS = DPC_STATE_ANNOTATIONS_BASE.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Policy::addGeneratedStates));
    private static final int APPLIED_BY_FLAGS =
            APPLIED_BY_DEVICE_OWNER | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER
                    | APPLIED_BY_PARENT_INSTANCE_OF_PROFILE_OWNER;
    private static final Map<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>>
            ANNOTATIONS_MAP = calculateAnnotationsMap(STATE_ANNOTATIONS);

    private Policy() {

    }

    @AutoAnnotation
    private static EnsureTestAppInstalled ensureTestAppInstalled(
            String packageName, UserType onUser, boolean isPrimary) {
        return new AutoAnnotation_Policy_ensureTestAppInstalled(packageName, onUser, isPrimary);
    }

    @AutoAnnotation
    private static EnsureTestAppHasPermission ensureTestAppHasPermission(String[] value) {
        return new AutoAnnotation_Policy_ensureTestAppHasPermission(value);
    }

    @AutoAnnotation
    private static EnsureTestAppHasAppOp ensureTestAppHasAppOp(String[] value) {
        return new AutoAnnotation_Policy_ensureTestAppHasAppOp(value);
    }

    @AutoAnnotation
    private static IncludeNone includeNone() {
        return new AutoAnnotation_Policy_includeNone();
    }

    @AutoAnnotation
    private static IncludeRunOnDeviceOwnerUser includeRunOnDeviceOwnerUser() {
        return new AutoAnnotation_Policy_includeRunOnDeviceOwnerUser();
    }

    @AutoAnnotation
    private static IncludeRunOnUnaffiliatedDeviceOwnerSecondaryUser includeRunOnNonAffiliatedDeviceOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnNonAffiliatedDeviceOwnerSecondaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnAffiliatedDeviceOwnerSecondaryUser includeRunOnAffiliatedDeviceOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnAffiliatedDeviceOwnerSecondaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnAffiliatedProfileOwnerSecondaryUser includeRunOnAffiliatedProfileOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnAffiliatedProfileOwnerSecondaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnUnaffiliatedProfileOwnerSecondaryUser includeRunOnUnaffiliatedProfileOwnerSecondaryUser() {
        return new AutoAnnotation_Policy_includeRunOnUnaffiliatedProfileOwnerSecondaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnProfileOwnerProfileWithNoDeviceOwner includeRunOnProfileOwnerProfileWithNoDeviceOwner() {
        return new AutoAnnotation_Policy_includeRunOnProfileOwnerProfileWithNoDeviceOwner();
    }

    @AutoAnnotation
    private static IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile() {
        return new AutoAnnotation_Policy_includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile();
    }

    @AutoAnnotation
    private static IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner includeRunOnParentOfProfileOwnerWithNoDeviceOwner() {
        return new AutoAnnotation_Policy_includeRunOnParentOfProfileOwnerWithNoDeviceOwner();
    }

    @AutoAnnotation
    private static IncludeRunOnParentOfCorporateOwnedProfileOwner includeRunOnParentOfCorporateOwnedProfileOwner() {
        return new AutoAnnotation_Policy_includeRunOnParentOfCorporateOwnedProfileOwner();
    }

    @AutoAnnotation
    private static IncludeRunOnProfileOwnerPrimaryUser includeRunOnProfileOwnerPrimaryUser() {
        return new AutoAnnotation_Policy_includeRunOnProfileOwnerPrimaryUser();
    }

    @AutoAnnotation
    private static IncludeRunOnBackgroundDeviceOwnerUser includeRunOnBackgroundDeviceOwnerUser() {
        return new AutoAnnotation_Policy_includeRunOnBackgroundDeviceOwnerUser();
    }

    @AutoAnnotation
    private static EnsureHasDelegate ensureHasDelegate(EnsureHasDelegate.AdminType admin,
            String[] scopes, boolean isPrimary) {
        return new AutoAnnotation_Policy_ensureHasDelegate(admin, scopes, isPrimary);
    }

    @AutoAnnotation
    private static IncludeRunOnParentOfProfileOwnerUsingParentInstance includeRunOnParentOfProfileOwnerUsingParentInstance() {
        return new AutoAnnotation_Policy_includeRunOnParentOfProfileOwnerUsingParentInstance();
    }

    private static Function<EnterprisePolicy, Set<Annotation>> singleAnnotation(
            Annotation annotation) {
        return (i) -> ImmutableSet.of(annotation);
    }

    private static Function<EnterprisePolicy, Set<Annotation>> generateDelegateAnnotation(
            Annotation annotation, boolean isPrimary) {
        return (policy) -> {
            Annotation[] existingAnnotations = annotation.annotationType().getAnnotations();
            return Arrays.stream(policy.delegatedScopes())
                    .map(scope -> {
                        Annotation[] newAnnotations = Arrays.copyOf(existingAnnotations,
                                existingAnnotations.length + 1);
                        newAnnotations[newAnnotations.length - 1] = ensureHasDelegate(
                                EnsureHasDelegate.AdminType.PRIMARY, new String[]{scope},
                                isPrimary);

                        return new DynamicParameterizedAnnotation(
                                annotation.annotationType().getSimpleName() + "Delegate:" + scope,
                                newAnnotations);
                    }).collect(Collectors.toSet());
        };
    }

    private static Map<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> calculateAnnotationsMap(
            Map<Integer, Function<EnterprisePolicy, Set<Annotation>>> annotations) {
        Map<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> b = new HashMap<>();

        for (Map.Entry<Integer, Function<EnterprisePolicy, Set<Annotation>>> i :
                annotations.entrySet()) {
            if (!b.containsKey(i.getValue())) {
                b.put(i.getValue(), new HashSet<>());
            }

            b.get(i.getValue()).add(i.getKey());
        }

        return b;
    }

    private static Function<EnterprisePolicy, Set<Annotation>> addGeneratedStates(
            ImmutableMap.Entry<Integer, Function<EnterprisePolicy, Set<Annotation>>> entry) {
        return (policy) -> {
            if (hasFlag(policy.dpc(), entry.getKey() | CAN_BE_DELEGATED)) {
                Set<Annotation> results = new HashSet<>(entry.getValue().apply(policy));
                results.addAll(results.stream().flatMap(
                        t -> generateDelegateAnnotation(t, /* isPrimary= */ true).apply(
                                policy).stream())
                        .collect(Collectors.toSet()));

                return results;
            }

            return entry.getValue().apply(policy);
        };
    }

    /**
     * Get parameterized test runs for the given policy.
     *
     * <p>These are states which should be run where the policy is able to be applied.
     */
    public static List<Annotation> policyAppliesStates(String policyName,
            EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        validateFlags(policyName, enterprisePolicy.dpc());

        for (Map.Entry<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> annotation :
                ANNOTATIONS_MAP.entrySet()) {
            if (policyWillApply(enterprisePolicy.dpc(), annotation.getValue())) {
                annotations.addAll(annotation.getKey().apply(enterprisePolicy));
            }
        }

        for (AppOp appOp : enterprisePolicy.appOps()) {
            // TODO(b/219750042): Currently we only test that app ops apply to the current user
            Annotation[] withAppOpAnnotations = new Annotation[]{
                    ensureTestAppInstalled(DELEGATE_PACKAGE_NAME,
                            UserType.INSTRUMENTED_USER, /* isPrimary= */ true),
                    ensureTestAppHasAppOp(new String[]{appOp.appliedWith()})
            };
            annotations.add(
                    new DynamicParameterizedAnnotation(
                            "AppOp:" + appOp.appliedWith(), withAppOpAnnotations));
        }

        removeShadowingAnnotations(annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    private static boolean policyWillApply(int[] policyFlags, Set<Integer> annotationFlags) {
        for (int annotationFlag : annotationFlags) {
            if (hasFlag(policyFlags, annotationFlag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean policyWillNotApply(int[] policyFlags, Set<Integer> annotationFlags) {
        for (int annotationFlag : annotationFlags) {
            if (hasFlag(annotationFlag,
                    DO_NOT_APPLY_TO_POLICY_DOES_NOT_APPLY_TESTS, /* nonMatchingFlag= */ NO)) {
                return false; // We don't support using this annotation for PolicyDoesNotApply tests
            }

            int appliedByFlag = APPLIED_BY_FLAGS & annotationFlag;
            int otherFlags = annotationFlag ^ appliedByFlag; // remove the appliedByFlag
            if (hasFlag(policyFlags, /* matchingFlag= */ appliedByFlag, /* nonMatchingFlag= */
                    otherFlags)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get parameterized test runs for the given policy.
     *
     * <p>These are states which should be run where the policy is not able to be applied.
     */
    public static List<Annotation> policyDoesNotApplyStates(String policyName,
            EnterprisePolicy enterprisePolicy) {
        Set<Annotation> annotations = new HashSet<>();

        validateFlags(policyName, enterprisePolicy.dpc());

        for (Map.Entry<Function<EnterprisePolicy, Set<Annotation>>, Set<Integer>> annotation :
                ANNOTATIONS_MAP.entrySet()) {
            if (policyWillNotApply(enterprisePolicy.dpc(), annotation.getValue())) {
                annotations.addAll(annotation.getKey().apply(enterprisePolicy));
            }
        }

        removeShadowedAnnotations(annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    /**
     * Get parameterized test runs where the policy cannot be set for the given policy.
     */
    public static List<Annotation> cannotSetPolicyStates(String policyName,
            EnterprisePolicy enterprisePolicy, boolean includeDeviceAdminStates,
            boolean includeNonDeviceAdminStates) {
        Set<Annotation> annotations = new HashSet<>();

        validateFlags(policyName, enterprisePolicy.dpc());

        if (includeDeviceAdminStates) {
            int allFlags = 0;
            for (int p : enterprisePolicy.dpc()) {
                allFlags = allFlags | p;
            }

            for (Map.Entry<Integer, Function<EnterprisePolicy, Set<Annotation>>> appliedByFlag :
                    DPC_STATE_ANNOTATIONS.entrySet()) {
                if ((appliedByFlag.getKey() & allFlags) == 0) {
                    annotations.addAll(appliedByFlag.getValue().apply(enterprisePolicy));
                }
            }
        }

        if (includeNonDeviceAdminStates) {
            Set<String> validScopes = ImmutableSet.copyOf(enterprisePolicy.delegatedScopes());
            String[] scopes = ALL_DELEGATE_SCOPES.stream()
                    .filter(i -> !validScopes.contains(i))
                    .toArray(String[]::new);
            Annotation[] existingAnnotations = IncludeRunOnDeviceOwnerUser.class.getAnnotations();

            if (BedsteadJUnit4.isDebug()) {
                // Add a non-DPC with no delegate scopes
                Annotation[] newAnnotations = Arrays.copyOf(existingAnnotations,
                        existingAnnotations.length + 1);
                newAnnotations[newAnnotations.length - 1] = ensureHasDelegate(
                        EnsureHasDelegate.AdminType.PRIMARY, new String[]{},
                        /* isPrimary= */ true);
                annotations.add(
                        new DynamicParameterizedAnnotation("DelegateWithNoScopes", newAnnotations));

                for (String scope : scopes) {
                    newAnnotations = Arrays.copyOf(existingAnnotations,
                            existingAnnotations.length + 1);
                    newAnnotations[newAnnotations.length - 1] = ensureHasDelegate(
                            EnsureHasDelegate.AdminType.PRIMARY, new String[]{scope},
                            /* isPrimary= */ true);
                    annotations.add(
                            new DynamicParameterizedAnnotation("DelegateWithScope:" + scope, newAnnotations));
                }
            } else {
                Annotation[] newAnnotations = Arrays.copyOf(existingAnnotations,
                        existingAnnotations.length + 1);
                newAnnotations[newAnnotations.length - 1] = ensureHasDelegate(
                        EnsureHasDelegate.AdminType.PRIMARY, scopes, /* isPrimary= */ true);
                annotations.add(
                        new DynamicParameterizedAnnotation("DelegateWithoutValidScope", newAnnotations));
            }
        }

        removeShadowedAnnotations(annotations);

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        return new ArrayList<>(annotations);
    }

    /**
     * Get state annotations where the policy can be set for the given policy.
     */
    public static List<Annotation> canSetPolicyStates(
            String policyName, EnterprisePolicy enterprisePolicy, boolean singleTestOnly) {
        Set<Annotation> annotations = new HashSet<>();

        validateFlags(policyName, enterprisePolicy.dpc());

        int allFlags = 0;
        for (int p : enterprisePolicy.dpc()) {
            allFlags = allFlags | p;
        }

        for (Map.Entry<Integer, Function<EnterprisePolicy, Set<Annotation>>> appliedByFlag :
                DPC_STATE_ANNOTATIONS.entrySet()) {
            if ((appliedByFlag.getKey() & allFlags) == appliedByFlag.getKey()) {
                annotations.addAll(appliedByFlag.getValue().apply(enterprisePolicy));
            }
        }

        if (annotations.isEmpty()) {
            // Don't run the original test unparameterized
            annotations.add(includeNone());
        }

        for (AppOp appOp : enterprisePolicy.appOps()) {
            // TODO(b/219750042): Currently we only test that app ops can be set as the primary user
            Annotation[] withAppOpAnnotations = new Annotation[]{
                    ensureTestAppInstalled(
                            DELEGATE_PACKAGE_NAME, UserType.INSTRUMENTED_USER,
                            /* isPrimary= */ true),
                    ensureTestAppHasAppOp(new String[]{appOp.appliedWith()})
            };
            annotations.add(
                    new DynamicParameterizedAnnotation(
                            "AppOp:" + appOp.appliedWith(), withAppOpAnnotations));
        }


        List<Annotation> annotationList = new ArrayList<>(annotations);

        removeShadowingAnnotations(annotations);

        if (singleTestOnly) {
            // We select one annotation in an arbitrary but deterministic way
            annotationList.sort(Comparator.comparing(
                    a -> a instanceof DynamicParameterizedAnnotation
                            ? "DynamicParameterizedAnnotation" : a.annotationType().getName()));

            // We don't want a delegate to be the representative test
            Annotation firstAnnotation = annotationList.stream()
                    .filter(i -> !(i instanceof  DynamicParameterizedAnnotation))
                    .findFirst().get();
            annotationList.clear();
            annotationList.add(firstAnnotation);
        }

        return annotationList;
    }

    private static void validateFlags(String policyName, int[] values) {
        int usedAppliedByFlags = 0;

        for (int value : values) {
            validateFlags(policyName, value);
            int newUsedAppliedByFlags = usedAppliedByFlags | (value & APPLIED_BY_FLAGS);
            if (newUsedAppliedByFlags == usedAppliedByFlags) {
                throw new IllegalStateException(
                        "Cannot have more than one policy flag APPLIED by the same component. "
                                + "Error in policy " + policyName);
            }
            usedAppliedByFlags = newUsedAppliedByFlags;
        }
    }

    private static void validateFlags(String policyName, int value) {
        int matchingAppliedByFlags = APPLIED_BY_FLAGS & value;

        if (matchingAppliedByFlags == 0) {
            throw new IllegalStateException(
                    "All policy flags must specify 1 APPLIED_BY flag. Policy " + policyName
                            + " did not.");
        }
    }

    private static boolean hasFlag(int[] values, int matchingFlag) {
        return hasFlag(values, matchingFlag, /* nonMatchingFlag= */ NO);
    }

    private static boolean hasFlag(int[] values, int matchingFlag, int nonMatchingFlag) {
        for (int value : values) {
            if (hasFlag(value, matchingFlag, nonMatchingFlag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFlag(int value, int matchingFlag, int nonMatchingFlag) {
        if (!((value & matchingFlag) == matchingFlag)) {
            return false;
        }

        if (nonMatchingFlag != NO) {
            return (value & nonMatchingFlag) != nonMatchingFlag;
        }

        return true;
    }

    /**
     * Remove entries from {@code annotations} which are shadowed by another entry
     * in {@code annotatipns} (directly or indirectly).
     */
    private static void removeShadowedAnnotations(Set<Annotation> annotations) {
        Set<Class<? extends Annotation>> shadowedAnnotations = new HashSet<>();
        for (Annotation annotation : annotations) {
            if (annotation instanceof DynamicParameterizedAnnotation) {
                continue; // Doesn't shadow anything
            }

            ParameterizedAnnotation parameterizedAnnotation =
                    annotation.annotationType().getAnnotation(ParameterizedAnnotation.class);

            if (parameterizedAnnotation == null) {
                continue; // Not parameterized
            }

            for (Class<? extends Annotation> shadowedAnnotationClass
                    : parameterizedAnnotation.shadows()) {
                addShadowed(shadowedAnnotations, shadowedAnnotationClass);
            }
        }

        annotations.removeIf(a -> shadowedAnnotations.contains(a.annotationType()));
    }

    private static void addShadowed(Set<Class<? extends Annotation>> shadowedAnnotations,
            Class<? extends Annotation> annotationClass) {
        shadowedAnnotations.add(annotationClass);
        ParameterizedAnnotation parameterizedAnnotation =
                annotationClass.getAnnotation(ParameterizedAnnotation.class);

        if (parameterizedAnnotation == null) {
            return;
        }

        for (Class<? extends Annotation> shadowedAnnotationClass
                : parameterizedAnnotation.shadows()) {
            addShadowed(shadowedAnnotations, shadowedAnnotationClass);
        }
    }

    // This maps classes to classes which shadow them - we just need to ensure it contains all
    // annotation classes we encounter
    private static Map<Class<? extends Annotation>, Set<Class<? extends Annotation>>>
            sReverseShadowMap = new HashMap<>();

    /**
     * Remove entries from {@code annotations} which are shadowing another entry
     * in {@code annotatipns} (directly or indirectly).
     */
    private static void removeShadowingAnnotations(Set<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            recordInReverseShadowMap(annotation);
        }

        Set<Class<? extends Annotation>> shadowingAnnotations = new HashSet<>();

        for (Annotation annotation : annotations) {
            shadowingAnnotations.addAll(
                    sReverseShadowMap.getOrDefault(annotation.annotationType(), Set.of()));
        }

        annotations.removeIf(a -> shadowingAnnotations.contains(a.annotationType()));
    }

    private static void recordInReverseShadowMap(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return; // Not shadowed by anything
        }

        ParameterizedAnnotation parameterizedAnnotation =
                annotation.annotationType().getAnnotation(ParameterizedAnnotation.class);

        if (parameterizedAnnotation == null) {
            return; // Not parameterized
        }

        if (parameterizedAnnotation.shadows().length == 0) {
            return; // Doesn't shadow anything
        }

        recordShadowedInReverseShadowMap(annotation.annotationType(), parameterizedAnnotation);
    }

    private static void recordShadowedInReverseShadowMap(Class<? extends Annotation> annotation,
            ParameterizedAnnotation parameterizedAnnotation) {
        for (Class<? extends Annotation> shadowedAnnotation : parameterizedAnnotation.shadows()) {
            ParameterizedAnnotation shadowedParameterizedAnnotation =
                    shadowedAnnotation.getAnnotation(ParameterizedAnnotation.class);

            if (shadowedParameterizedAnnotation == null) {
                continue; // Not parameterized
            }

            if (!sReverseShadowMap.containsKey(shadowedAnnotation)) {
                sReverseShadowMap.put(shadowedAnnotation, new HashSet<>());
            }

            sReverseShadowMap.get(shadowedAnnotation).add(annotation);

            recordShadowedInReverseShadowMap(annotation, shadowedParameterizedAnnotation);
        }
    }
}
