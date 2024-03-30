package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.S;
import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Locale;
import libcore.icu.LocaleData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(AndroidJUnit4.class)
public class ShadowLocaleDataTest {

  @Test
  @Config(maxSdk = Build.VERSION_CODES.S)
  public void shouldSupportLocaleEn_US() throws Exception {
    LocaleData localeData = LocaleData.get(Locale.US);

    assertThat(localeData.amPm).isEqualTo(new String[]{"AM", "PM"});
    assertThat(localeData.eras).isEqualTo(new String[]{"BC", "AD"});

    assertThat(localeData.firstDayOfWeek).isEqualTo(1);
    assertThat(localeData.minimalDaysInFirstWeek).isEqualTo(1);

    assertThat(localeData.longMonthNames).isEqualTo(new String[]{"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"});
    assertThat(localeData.shortMonthNames).isEqualTo(new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"});

    assertThat(localeData.longStandAloneMonthNames).isEqualTo(localeData.longMonthNames);
    assertThat(localeData.shortStandAloneMonthNames).isEqualTo(localeData.shortMonthNames);

    assertThat(localeData.longWeekdayNames).isEqualTo(new String[]{"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"});
    assertThat(localeData.shortWeekdayNames).isEqualTo(new String[]{"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"});

    assertThat(localeData.longStandAloneWeekdayNames).isEqualTo(localeData.longWeekdayNames);
    assertThat(localeData.shortStandAloneWeekdayNames).isEqualTo(localeData.shortWeekdayNames);

    assertThat((String) ReflectionHelpers.getField(localeData, "fullTimeFormat")).isEqualTo("h:mm:ss a zzzz");
    assertThat((String) ReflectionHelpers.getField(localeData, "longTimeFormat")).isEqualTo("h:mm:ss a z");
    assertThat((String) ReflectionHelpers.getField(localeData, "mediumTimeFormat")).isEqualTo("h:mm:ss a");
    assertThat((String) ReflectionHelpers.getField(localeData, "shortTimeFormat")).isEqualTo("h:mm a");

    assertThat((String) ReflectionHelpers.getField(localeData, "fullDateFormat")).isEqualTo("EEEE, MMMM d, y");
    assertThat((String) ReflectionHelpers.getField(localeData, "longDateFormat")).isEqualTo("MMMM d, y");
    assertThat((String) ReflectionHelpers.getField(localeData, "mediumDateFormat")).isEqualTo("MMM d, y");
    assertThat((String) ReflectionHelpers.getField(localeData, "shortDateFormat")).isEqualTo("M/d/yy");

    assertThat((char) ReflectionHelpers.getField(localeData, "zeroDigit")).isEqualTo('0');
    assertThat((char) ReflectionHelpers.getField(localeData, "decimalSeparator")).isEqualTo('.');
    assertThat((char) ReflectionHelpers.getField(localeData, "groupingSeparator")).isEqualTo(',');
    assertThat((char) ReflectionHelpers.getField(localeData, "patternSeparator")).isEqualTo(';');

    assertThat((char) ReflectionHelpers.getField(localeData, "monetarySeparator")).isEqualTo('.');
    assertThat((char) ReflectionHelpers.getField(localeData, "perMill")).isEqualTo('‰');

    assertThat((String) ReflectionHelpers.getField(localeData, "exponentSeparator")).isEqualTo("E");
    assertThat((String) ReflectionHelpers.getField(localeData, "infinity")).isEqualTo("∞");
    assertThat((String) ReflectionHelpers.getField(localeData, "NaN")).isEqualTo("NaN");

    assertThat((String) ReflectionHelpers.getField(localeData, "numberPattern")).isEqualTo("#,##0.###");
    assertThat((String) ReflectionHelpers.getField(localeData, "integerPattern")).isEqualTo("#,##0");
    assertThat((String) ReflectionHelpers.getField(localeData, "currencyPattern")).isEqualTo("¤#,##0.00;(¤#,##0.00)");
    assertThat((String) ReflectionHelpers.getField(localeData, "percentPattern")).isEqualTo("#,##0%");
  }

  @Test
  @Config(maxSdk = Build.VERSION_CODES.Q)
  public void shouldSupportLocaleEn_US_yesterday() throws Exception {
    LocaleData localeData = LocaleData.get(Locale.US);
      String currencySymbolValue = ReflectionHelpers.getField(localeData, "yesterday");
      assertThat(currencySymbolValue).isEqualTo("Yesterday");
  }

  @Test
  @Config(maxSdk = Build.VERSION_CODES.Q)
  public void shouldSupportLocaleEn_US_currencySymbol() throws Exception {
    LocaleData localeData = LocaleData.get(Locale.US);
    String currencySymbolValue = ReflectionHelpers.getField(localeData, "currencySymbol");
    assertThat(currencySymbolValue).isEqualTo("$");
  }

  @Test
  @Config(maxSdk = Build.VERSION_CODES.Q)
  public void shouldSupportLocaleEn_US_internationalCurrencySymbol() throws Exception {
      LocaleData localeData = LocaleData.get(Locale.US);
      String internationalCurrencySymbolValue = ReflectionHelpers.getField(localeData, "internationalCurrencySymbol");
      assertThat(internationalCurrencySymbolValue).isEqualTo("USD");
  }

  @Test
  @Config(minSdk = LOLLIPOP_MR1, maxSdk = S)
  public void shouldSupportLocaleEn_US_percentPost22() throws Exception {
    LocaleData localeData = LocaleData.get(Locale.US);
    assertThat((String) ReflectionHelpers.getField(localeData, "percent")).isEqualTo("%");
  }

  @Test
  @Config(minSdk = JELLY_BEAN_MR1)
  public void shouldSupportLocaleEn_US_since_jelly_bean_mr1() throws Exception {
    LocaleData localeData = LocaleData.get(Locale.US);

    assertThat(localeData.tinyMonthNames).isEqualTo(new String[]{"J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"});
    assertThat(localeData.tinyStandAloneMonthNames).isEqualTo(localeData.tinyMonthNames);
    assertThat(localeData.tinyWeekdayNames).isEqualTo(new String[]{"", "S", "M", "T", "W", "T", "F", "S"});
    assertThat(localeData.tinyStandAloneWeekdayNames).isEqualTo(localeData.tinyWeekdayNames);

    assertThat(localeData.today).isEqualTo("Today");
    assertThat(localeData.tomorrow).isEqualTo("Tomorrow");
  }

  @Test
  @Config(minSdk = M)
  public void shouldSupportLocaleEn_US_since_m() throws Exception {
    LocaleData localeData = LocaleData.get(Locale.US);

    assertThat(localeData.timeFormat_Hm).isEqualTo("HH:mm");
    assertThat(localeData.timeFormat_hm).isEqualTo("h:mm a");
  }

  @Test
  @Config(minSdk = LOLLIPOP, maxSdk = S)
  public void shouldSupportLocaleEn_US_since_lollipop() throws Exception {
    LocaleData localeData = LocaleData.get(Locale.US);

    assertThat((String) ReflectionHelpers.getField(localeData, "minusSign")).isEqualTo("-");
  }

  @Test
  public void shouldDefaultToTheDefaultLocale() throws Exception {
    Locale.setDefault(Locale.US);
    LocaleData localeData = LocaleData.get(null);

    assertThat(localeData.amPm).isEqualTo(new String[]{"AM", "PM"});
  }
}
