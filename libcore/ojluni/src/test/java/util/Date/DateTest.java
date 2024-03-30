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

/*
 * @test
 * @bug 4143459
 * @summary test Date
 * @library /java/text/testlib
 */
package test.java.util.Date;

import java.util.*;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DateTest {

    /**
     * Verify that the Date(String) constructor works.
     */
    @Test
    public void testParseOfGMT()
    {
        Date OUT;

        /* Input values */
        String stringVal = "Jan 01 00:00:00 GMT 1900";
        long expectedVal = -2208988800000L;

        OUT = new Date( stringVal );

        Assert.assertEquals(OUT.getTime( ), expectedVal );
    }

    // Check out Date's behavior with large negative year values; bug 664
    // As of the fix to bug 4056585, Date should work correctly with
    // large negative years.
    @Test
    public void testDateNegativeYears()
    {
        Date d1= new Date(80,-1,2);
        d1= new Date(-80,-1,2);
        try {
            d1= new Date(-800000,-1,2);
        }
        catch (IllegalArgumentException ex) {
            Assert.fail();
        }
    }

    // Verify the behavior of Date
    @Test
    public void testDate480()
    {
        TimeZone save = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("PST"));
            Date d1=new java.util.Date(97,8,13,10,8,13);
            Date d2=new java.util.Date(97,8,13,30,8,13); // 20 hours later

            double delta = (d2.getTime() - d1.getTime()) / 3600000;


            Assert.assertEquals(delta, 20.0);

            Calendar cal = Calendar.getInstance();
            cal.clear();
            cal.set(1997,8,13,10,8,13);
            Date t1 = cal.getTime();
            cal.clear();
            cal.set(1997,8,13,30,8,13); // 20 hours later
            Date t2 = cal.getTime();

            double delta2 = (t2.getTime() - t1.getTime()) / 3600000;

            Assert.assertEquals(delta2, 20.0);
        }
        finally {
            TimeZone.setDefault(save);
        }
    }
}