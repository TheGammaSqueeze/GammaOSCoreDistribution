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

package com.android.safetycenter.config;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.StringRes;
import android.content.res.Resources;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;

import androidx.annotation.RequiresApi;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;

/** Parser and validator for {@link SafetyCenterConfig} objects. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterConfigParser {

    private static final String TAG_SAFETY_CENTER_CONFIG = "safety-center-config";
    private static final String TAG_SAFETY_SOURCES_CONFIG = "safety-sources-config";
    private static final String TAG_SAFETY_SOURCES_GROUP = "safety-sources-group";
    private static final String TAG_STATIC_SAFETY_SOURCE = "static-safety-source";
    private static final String TAG_DYNAMIC_SAFETY_SOURCE = "dynamic-safety-source";
    private static final String TAG_ISSUE_ONLY_SAFETY_SOURCE = "issue-only-safety-source";
    private static final String ATTR_SAFETY_SOURCES_GROUP_ID = "id";
    private static final String ATTR_SAFETY_SOURCES_GROUP_TITLE = "title";
    private static final String ATTR_SAFETY_SOURCES_GROUP_SUMMARY = "summary";
    private static final String ATTR_SAFETY_SOURCES_GROUP_STATELESS_ICON_TYPE = "statelessIconType";
    private static final String ATTR_SAFETY_SOURCE_ID = "id";
    private static final String ATTR_SAFETY_SOURCE_PACKAGE_NAME = "packageName";
    private static final String ATTR_SAFETY_SOURCE_TITLE = "title";
    private static final String ATTR_SAFETY_SOURCE_TITLE_FOR_WORK = "titleForWork";
    private static final String ATTR_SAFETY_SOURCE_SUMMARY = "summary";
    private static final String ATTR_SAFETY_SOURCE_INTENT_ACTION = "intentAction";
    private static final String ATTR_SAFETY_SOURCE_PROFILE = "profile";
    private static final String ATTR_SAFETY_SOURCE_INITIAL_DISPLAY_STATE = "initialDisplayState";
    private static final String ATTR_SAFETY_SOURCE_MAX_SEVERITY_LEVEL = "maxSeverityLevel";
    private static final String ATTR_SAFETY_SOURCE_SEARCH_TERMS = "searchTerms";
    private static final String ATTR_SAFETY_SOURCE_LOGGING_ALLOWED = "loggingAllowed";
    private static final String ATTR_SAFETY_SOURCE_REFRESH_ON_PAGE_OPEN_ALLOWED =
            "refreshOnPageOpenAllowed";
    private static final String ENUM_STATELESS_ICON_TYPE_NONE = "none";
    private static final String ENUM_STATELESS_ICON_TYPE_PRIVACY = "privacy";
    private static final String ENUM_PROFILE_PRIMARY = "primary_profile_only";
    private static final String ENUM_PROFILE_ALL = "all_profiles";
    private static final String ENUM_INITIAL_DISPLAY_STATE_ENABLED = "enabled";
    private static final String ENUM_INITIAL_DISPLAY_STATE_DISABLED = "disabled";
    private static final String ENUM_INITIAL_DISPLAY_STATE_HIDDEN = "hidden";

    private SafetyCenterConfigParser() {}

    /**
     * Parses and validates the given XML resource into a {@link SafetyCenterConfig} object.
     *
     * <p>It throws a {@link ParseException} if the given XML resource does not comply with the
     * safety_center_config.xsd schema.
     *
     * @param in the raw XML resource representing the Safety Center configuration
     * @param resources the {@link Resources} retrieved from the package that contains the Safety
     *     Center configuration
     */
    @NonNull
    public static SafetyCenterConfig parseXmlResource(
            @NonNull InputStream in, @NonNull Resources resources) throws ParseException {
        requireNonNull(in);
        requireNonNull(resources);
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setFeature(FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(in, null);
            if (parser.getEventType() != START_DOCUMENT) {
                throw new ParseException("Unexpected parser state");
            }
            parser.nextTag();
            validateElementStart(parser, TAG_SAFETY_CENTER_CONFIG);
            SafetyCenterConfig safetyCenterConfig = parseSafetyCenterConfig(parser, resources);
            if (parser.getEventType() == TEXT && parser.isWhitespace()) {
                parser.next();
            }
            if (parser.getEventType() != END_DOCUMENT) {
                throw new ParseException("Unexpected extra root element");
            }
            return safetyCenterConfig;
        } catch (XmlPullParserException | IOException e) {
            throw new ParseException("Exception while parsing the XML resource", e);
        }
    }

    @NonNull
    private static SafetyCenterConfig parseSafetyCenterConfig(
            @NonNull XmlPullParser parser, @NonNull Resources resources)
            throws XmlPullParserException, IOException, ParseException {
        validateElementHasNoAttribute(parser, TAG_SAFETY_CENTER_CONFIG);
        parser.nextTag();
        validateElementStart(parser, TAG_SAFETY_SOURCES_CONFIG);
        validateElementHasNoAttribute(parser, TAG_SAFETY_SOURCES_CONFIG);
        SafetyCenterConfig.Builder builder = new SafetyCenterConfig.Builder();
        parser.nextTag();
        while (parser.getEventType() == START_TAG
                && parser.getName().equals(TAG_SAFETY_SOURCES_GROUP)) {
            builder.addSafetySourcesGroup(parseSafetySourcesGroup(parser, resources));
        }
        validateElementEnd(parser, TAG_SAFETY_SOURCES_CONFIG);
        parser.nextTag();
        validateElementEnd(parser, TAG_SAFETY_CENTER_CONFIG);
        parser.next();
        try {
            return builder.build();
        } catch (IllegalStateException e) {
            throw elementInvalid(TAG_SAFETY_SOURCES_CONFIG, e);
        }
    }

    @NonNull
    private static SafetySourcesGroup parseSafetySourcesGroup(
            @NonNull XmlPullParser parser, @NonNull Resources resources)
            throws XmlPullParserException, IOException, ParseException {
        String name = TAG_SAFETY_SOURCES_GROUP;
        SafetySourcesGroup.Builder builder = new SafetySourcesGroup.Builder();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case ATTR_SAFETY_SOURCES_GROUP_ID:
                    builder.setId(
                            parseStringResourceValue(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCES_GROUP_TITLE:
                    builder.setTitleResId(
                            parseStringResourceName(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCES_GROUP_SUMMARY:
                    builder.setSummaryResId(
                            parseStringResourceName(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCES_GROUP_STATELESS_ICON_TYPE:
                    builder.setStatelessIconType(
                            parseStatelessIconType(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                default:
                    throw attributeUnexpected(name, parser.getAttributeName(i));
            }
        }
        parser.nextTag();
        loop:
        while (parser.getEventType() == START_TAG) {
            int type;
            switch (parser.getName()) {
                case TAG_STATIC_SAFETY_SOURCE:
                    type = SafetySource.SAFETY_SOURCE_TYPE_STATIC;
                    break;
                case TAG_DYNAMIC_SAFETY_SOURCE:
                    type = SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC;
                    break;
                case TAG_ISSUE_ONLY_SAFETY_SOURCE:
                    type = SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY;
                    break;
                default:
                    break loop;
            }
            builder.addSafetySource(parseSafetySource(parser, resources, type, parser.getName()));
        }
        validateElementEnd(parser, name);
        parser.nextTag();
        try {
            return builder.build();
        } catch (IllegalStateException e) {
            throw elementInvalid(name, e);
        }
    }

    @NonNull
    private static SafetySource parseSafetySource(
            @NonNull XmlPullParser parser,
            @NonNull Resources resources,
            int safetySourceType,
            @NonNull String name)
            throws XmlPullParserException, IOException, ParseException {
        SafetySource.Builder builder = new SafetySource.Builder(safetySourceType);
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case ATTR_SAFETY_SOURCE_ID:
                    builder.setId(
                            parseStringResourceValue(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_PACKAGE_NAME:
                    builder.setPackageName(
                            parseStringResourceValue(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_TITLE:
                    builder.setTitleResId(
                            parseStringResourceName(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_TITLE_FOR_WORK:
                    builder.setTitleForWorkResId(
                            parseStringResourceName(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_SUMMARY:
                    builder.setSummaryResId(
                            parseStringResourceName(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_INTENT_ACTION:
                    builder.setIntentAction(
                            parseStringResourceValue(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_PROFILE:
                    builder.setProfile(
                            parseProfile(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_INITIAL_DISPLAY_STATE:
                    builder.setInitialDisplayState(
                            parseInitialDisplayState(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_MAX_SEVERITY_LEVEL:
                    builder.setMaxSeverityLevel(
                            parseInteger(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_SEARCH_TERMS:
                    builder.setSearchTermsResId(
                            parseStringResourceName(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_LOGGING_ALLOWED:
                    builder.setLoggingAllowed(
                            parseBoolean(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                case ATTR_SAFETY_SOURCE_REFRESH_ON_PAGE_OPEN_ALLOWED:
                    builder.setRefreshOnPageOpenAllowed(
                            parseBoolean(
                                    parser.getAttributeValue(i),
                                    name,
                                    parser.getAttributeName(i),
                                    resources));
                    break;
                default:
                    throw attributeUnexpected(name, parser.getAttributeName(i));
            }
        }
        parser.nextTag();
        validateElementEnd(parser, name);
        parser.nextTag();
        try {
            return builder.build();
        } catch (IllegalStateException e) {
            throw elementInvalid(name, e);
        }
    }

    private static void validateElementStart(@NonNull XmlPullParser parser, @NonNull String name)
            throws XmlPullParserException, ParseException {
        if (parser.getEventType() != START_TAG || !parser.getName().equals(name)) {
            throw elementMissing(name);
        }
    }

    private static void validateElementEnd(@NonNull XmlPullParser parser, @NonNull String name)
            throws XmlPullParserException, ParseException {
        if (parser.getEventType() != END_TAG || !parser.getName().equals(name)) {
            throw elementNotClosed(name);
        }
    }

    private static void validateElementHasNoAttribute(
            @NonNull XmlPullParser parser, @NonNull String name) throws ParseException {
        if (parser.getAttributeCount() != 0) {
            throw elementInvalid(name);
        }
    }

    private static ParseException elementMissing(@NonNull String name) {
        return new ParseException(String.format("Element %s missing", name));
    }

    private static ParseException elementNotClosed(@NonNull String name) {
        return new ParseException(String.format("Element %s not closed", name));
    }

    private static ParseException elementInvalid(@NonNull String name) {
        return new ParseException(String.format("Element %s invalid", name));
    }

    private static ParseException elementInvalid(@NonNull String name, @NonNull Throwable e) {
        return new ParseException(String.format("Element %s invalid", name), e);
    }

    private static ParseException attributeUnexpected(
            @NonNull String parent, @NonNull String name) {
        return new ParseException(String.format("Unexpected attribute %s.%s", parent, name));
    }

    private static String attributeInvalidString(
            @NonNull String valueString, @NonNull String parent, @NonNull String name) {
        return String.format("Attribute value \"%s\" in %s.%s invalid", valueString, parent, name);
    }

    private static ParseException attributeInvalid(
            @NonNull String valueString, @NonNull String parent, @NonNull String name) {
        return new ParseException(attributeInvalidString(valueString, parent, name));
    }

    private static ParseException attributeInvalid(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Throwable ex) {
        return new ParseException(attributeInvalidString(valueString, parent, name), ex);
    }

    private static int parseInteger(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Resources resources)
            throws ParseException {
        String valueToParse = getValueToParse(valueString, parent, name, resources);
        try {
            return Integer.parseInt(valueToParse);
        } catch (NumberFormatException e) {
            throw attributeInvalid(valueToParse, parent, name, e);
        }
    }

    private static boolean parseBoolean(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Resources resources)
            throws ParseException {
        String valueToParse =
                getValueToParse(valueString, parent, name, resources).toLowerCase(ROOT);
        if (valueToParse.equals("true")) {
            return true;
        } else if (!valueToParse.equals("false")) {
            throw attributeInvalid(valueToParse, parent, name);
        }
        return false;
    }

    @StringRes
    private static int parseStringResourceName(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Resources resources)
            throws ParseException {
        if (valueString.isEmpty()) {
            throw new ParseException(
                    String.format("Resource name in %s.%s cannot be empty", parent, name));
        }
        if (valueString.charAt(0) != '@') {
            throw new ParseException(
                    String.format(
                            "Resource name \"%s\" in %s.%s does not start with @",
                            valueString, parent, name));
        }
        String[] colonSplit = valueString.substring(1).split(":", 2);
        if (colonSplit.length != 2 || colonSplit[0].isEmpty()) {
            throw new ParseException(
                    String.format(
                            "Resource name \"%s\" in %s.%s does not specify a package",
                            valueString, parent, name));
        }
        String packageName = colonSplit[0];
        String[] slashSplit = colonSplit[1].split("/", 2);
        if (slashSplit.length != 2 || slashSplit[0].isEmpty()) {
            throw new ParseException(
                    String.format(
                            "Resource name \"%s\" in %s.%s does not specify a type",
                            valueString, parent, name));
        }
        String type = slashSplit[0];
        if (!type.equals("string")) {
            throw new ParseException(
                    String.format(
                            "Resource name \"%s\" in %s.%s is not a string",
                            valueString, parent, name));
        }
        String entry = slashSplit[1];
        int id = resources.getIdentifier(entry, type, packageName);
        if (id == Resources.ID_NULL) {
            throw new ParseException(
                    String.format(
                            "Resource name \"%s\" in %s.%s missing or invalid",
                            valueString, parent, name));
        }
        return id;
    }

    @NonNull
    private static String parseStringResourceValue(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Resources resources) {
        return getValueToParse(valueString, parent, name, resources);
    }

    private static int parseStatelessIconType(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Resources resources)
            throws ParseException {
        String valueToParse = getValueToParse(valueString, parent, name, resources);
        switch (valueToParse) {
            case ENUM_STATELESS_ICON_TYPE_NONE:
                return SafetySourcesGroup.STATELESS_ICON_TYPE_NONE;
            case ENUM_STATELESS_ICON_TYPE_PRIVACY:
                return SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY;
            default:
                throw attributeInvalid(valueToParse, parent, name);
        }
    }

    private static int parseProfile(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Resources resources)
            throws ParseException {
        String valueToParse = getValueToParse(valueString, parent, name, resources);
        switch (valueToParse) {
            case ENUM_PROFILE_PRIMARY:
                return SafetySource.PROFILE_PRIMARY;
            case ENUM_PROFILE_ALL:
                return SafetySource.PROFILE_ALL;
            default:
                throw attributeInvalid(valueToParse, parent, name);
        }
    }

    private static int parseInitialDisplayState(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Resources resources)
            throws ParseException {
        String valueToParse = getValueToParse(valueString, parent, name, resources);
        switch (valueToParse) {
            case ENUM_INITIAL_DISPLAY_STATE_ENABLED:
                return SafetySource.INITIAL_DISPLAY_STATE_ENABLED;
            case ENUM_INITIAL_DISPLAY_STATE_DISABLED:
                return SafetySource.INITIAL_DISPLAY_STATE_DISABLED;
            case ENUM_INITIAL_DISPLAY_STATE_HIDDEN:
                return SafetySource.INITIAL_DISPLAY_STATE_HIDDEN;
            default:
                throw attributeInvalid(valueToParse, parent, name);
        }
    }

    @NonNull
    private static String getValueToParse(
            @NonNull String valueString,
            @NonNull String parent,
            @NonNull String name,
            @NonNull Resources resources) {
        try {
            int id = parseStringResourceName(valueString, parent, name, resources);
            return resources.getString(id);
        } catch (ParseException e) {
            return valueString;
        }
    }
}
