/*
 * Copyright (C) 2009 The Android Open Source Project
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

package libcore.icu;

import com.android.icu.util.ExtendedCalendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pattern cache for {@link SimpleDateFormat}
 *
 * @hide
 */
public class SimpleDateFormatData {

    // TODO(http://b/217881004): Replace this with a LRU cache.
    private static final ConcurrentHashMap<String, SimpleDateFormatData> CACHE =
            new ConcurrentHashMap<>(/* initialCapacity */ 3);

    private final Locale locale;

    private final String fullTimeFormat;
    private final String longTimeFormat;
    private final String mediumTimeFormat;
    private final String shortTimeFormat;

    private final String fullDateFormat;
    private final String longDateFormat;
    private final String mediumDateFormat;
    private final String shortDateFormat;

    private SimpleDateFormatData(Locale locale) {
        this.locale = locale;

        // libcore's java.text supports Gregorian calendar only.
        ExtendedCalendar extendedCalendar = ICU.getExtendedCalendar(locale, "gregorian");

        String tmpFullTimeFormat = getDateTimeFormatString(extendedCalendar,
                android.icu.text.DateFormat.NONE, android.icu.text.DateFormat.FULL);

        // Fix up a couple of patterns.
        if (tmpFullTimeFormat != null) {
            // There are some full time format patterns in ICU that use the pattern character 'v'.
            // Java doesn't accept this, so we replace it with 'z' which has about the same result
            // as 'v', the timezone name.
            // 'v' -> "PT", 'z' -> "PST", v is the generic timezone and z the standard tz
            // "vvvv" -> "Pacific Time", "zzzz" -> "Pacific Standard Time"
            tmpFullTimeFormat = tmpFullTimeFormat.replace('v', 'z');
        }
        fullTimeFormat = tmpFullTimeFormat;

        longTimeFormat = getDateTimeFormatString(extendedCalendar,
                android.icu.text.DateFormat.NONE, android.icu.text.DateFormat.LONG);
        mediumTimeFormat = getDateTimeFormatString(extendedCalendar,
                android.icu.text.DateFormat.NONE, android.icu.text.DateFormat.MEDIUM);
        shortTimeFormat = getDateTimeFormatString(extendedCalendar,
                android.icu.text.DateFormat.NONE, android.icu.text.DateFormat.SHORT);
        fullDateFormat = getDateTimeFormatString(extendedCalendar,
                android.icu.text.DateFormat.FULL, android.icu.text.DateFormat.NONE);
        longDateFormat = getDateTimeFormatString(extendedCalendar,
                android.icu.text.DateFormat.LONG, android.icu.text.DateFormat.NONE);
        mediumDateFormat = getDateTimeFormatString(extendedCalendar,
                android.icu.text.DateFormat.MEDIUM, android.icu.text.DateFormat.NONE);
        shortDateFormat = getDateTimeFormatString(extendedCalendar,
                android.icu.text.DateFormat.SHORT, android.icu.text.DateFormat.NONE);
    }

    /**
     * Returns an instance.
     *
     * @param locale can't be null
     * @throws NullPointerException if {@code locale} is null
     * @return a {@link SimpleDateFormatData} instance
     */
    public static SimpleDateFormatData getInstance(Locale locale) {
        Objects.requireNonNull(locale, "locale can't be null");

        locale = LocaleData.getCompatibleLocaleForBug159514442(locale);

        final String languageTag = locale.toLanguageTag();

        SimpleDateFormatData data = CACHE.get(languageTag);
        if (data != null) {
            return data;
        }

        data = new SimpleDateFormatData(locale);
        SimpleDateFormatData prev = CACHE.putIfAbsent(languageTag, data);
        if (prev != null) {
            return prev;
        }
        return data;
    }

    /**
     * Ensure that we pull in the locale data for the root locale, en_US, and the user's default
     * locale. All devices must support the root locale and en_US, and they're used for various
     * system things. Pre-populating the cache is especially useful on Android because
     * we'll share this via the Zygote.
     */
    public static void initializeCacheInZygote() {
        getInstance(Locale.ROOT);
        getInstance(Locale.US);
        getInstance(Locale.getDefault());
    }

    /**
     * @throws AssertionError if style is not one of the 4 styles specified in {@link DateFormat}
     * @return a date pattern string
     */
    public String getDateFormat(int style) {
        switch (style) {
            case DateFormat.SHORT:
                return shortDateFormat;
            case DateFormat.MEDIUM:
                return mediumDateFormat;
            case DateFormat.LONG:
                return longDateFormat;
            case DateFormat.FULL:
                return fullDateFormat;
        }
        // TODO: fix this legacy behavior of throwing AssertionError introduced in
        //  the commit 6ca85c4.
        throw new AssertionError();
    }

    /**
     * @throws AssertionError if style is not one of the 4 styles specified in {@link DateFormat}
     * @return a time pattern string
     */
    public String getTimeFormat(int style) {
        // Do not cache ICU.getTimePattern() return value in the LocaleData instance
        // because most users do not enable this setting, hurts performance in critical path,
        // e.g. b/161846393, and ICU.getBestDateTimePattern will cache it in  ICU.CACHED_PATTERNS
        // on demand.
        switch (style) {
            case DateFormat.SHORT:
                if (DateFormat.is24Hour == null) {
                    return shortTimeFormat;
                } else {
                    return ICU.getTimePattern(locale, DateFormat.is24Hour, false);
                }
            case DateFormat.MEDIUM:
                if (DateFormat.is24Hour == null) {
                    return mediumTimeFormat;
                } else {
                    return ICU.getTimePattern(locale, DateFormat.is24Hour, true);
                }
            case DateFormat.LONG:
                // CLDR doesn't really have anything we can use to obey the 12-/24-hour preference.
                return longTimeFormat;
            case DateFormat.FULL:
                // CLDR doesn't really have anything we can use to obey the 12-/24-hour preference.
                return fullTimeFormat;
        }
        // TODO: fix this legacy behavior of throwing AssertionError introduced in
        //  the commit 6ca85c4.
        throw new AssertionError();
    }

    private static String getDateTimeFormatString(ExtendedCalendar extendedCalendar,
            int dateStyle, int timeStyle) {
        return ICU.transformIcuDateTimePattern_forJavaText(
                extendedCalendar.getDateTimePattern(dateStyle, timeStyle));
    }

}
