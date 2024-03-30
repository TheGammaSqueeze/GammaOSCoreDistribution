/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava

import org.junit.Test

class ConvertTest : DriverTest() {

    @Test
    fun `Test conversion flag`() {
        check(
            convertToJDiff = listOf(
                ConvertData(
                    """
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                      }
                    }
                    """,
                    outputFile =
                    """
                    <api name="convert-output1" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="MyTest1"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <constructor name="MyTest1"
                     type="test.pkg.MyTest1"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </constructor>
                    </class>
                    </package>
                    </api>
                    """
                ),
                ConvertData(
                    fromApi =
                    """
                    package test.pkg {
                      public class MyTest2 {
                      }
                    }
                    """,
                    outputFile =
                    """
                    <api name="convert-output2" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="MyTest2"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </class>
                    </package>
                    </api>
                    """
                )
            )
        )
    }

    @Test
    fun `Test convert new with compat mode and api strip`() {
        check(
            convertToJDiff = listOf(
                ConvertData(
                    strip = true,
                    fromApi =
                    """
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        method public java.lang.Double convert(java.lang.Float);
                        field public static final java.lang.String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
                        field public deprecated java.lang.Number myNumber;
                      }
                      public class MyTest2 {
                        ctor public MyTest2();
                        method public java.lang.Double convert(java.lang.Float);
                      }
                    }
                    package test.pkg.new {
                      public interface MyInterface {
                      }
                      public abstract class MyTest3 implements java.util.List {
                      }
                      public abstract class MyTest4 implements test.pkg.new.MyInterface {
                      }
                    }
                    """,
                    baseApi =
                    """
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        field public deprecated java.lang.Number myNumber;
                      }
                    }
                    """,
                    outputFile =
                    """
                    <api name="convert-output1" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="MyTest1"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <method name="convert"
                     return="java.lang.Double"
                     abstract="false"
                     native="false"
                     synchronized="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <parameter name="null" type="java.lang.Float">
                    </parameter>
                    </method>
                    <field name="ANY_CURSOR_ITEM_TYPE"
                     type="java.lang.String"
                     transient="false"
                     volatile="false"
                     value="&quot;vnd.android.cursor.item/*&quot;"
                     static="true"
                     final="true"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </field>
                    </class>
                    <class name="MyTest2"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <constructor name="MyTest2"
                     type="test.pkg.MyTest2"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </constructor>
                    <method name="convert"
                     return="java.lang.Double"
                     abstract="false"
                     native="false"
                     synchronized="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <parameter name="null" type="java.lang.Float">
                    </parameter>
                    </method>
                    </class>
                    </package>
                    <package name="test.pkg.new"
                    >
                    <interface name="MyInterface"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </interface>
                    <class name="MyTest3"
                     extends="java.lang.Object"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </class>
                    <class name="MyTest4"
                     extends="java.lang.Object"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <implements name="test.pkg.new.MyInterface">
                    </implements>
                    </class>
                    </package>
                    </api>
                    """
                )
            )
        )
    }

    @Test
    fun `Test convert new without compat mode and no strip`() {
        check(
            convertToJDiff = listOf(
                ConvertData(
                    strip = false,
                    fromApi =
                    """
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        method public java.lang.Double convert(java.lang.Float);
                        field public static final java.lang.String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
                        field public deprecated java.lang.Number myNumber;
                      }
                      public class MyTest2 {
                        ctor public MyTest2();
                        method public java.lang.Double convert(java.lang.Float);
                      }
                    }
                    package test.pkg.new {
                      public interface MyInterface {
                      }
                      public abstract class MyTest3 implements java.util.List {
                      }
                      public abstract class MyTest4 implements test.pkg.new.MyInterface {
                      }
                    }
                    """,
                    baseApi =
                    """
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        field public deprecated java.lang.Number myNumber;
                      }
                    }
                    """,
                    outputFile =
                    """
                    <api name="convert-output1" xmlns:metalava="http://www.android.com/metalava/">
                    <package name="test.pkg"
                    >
                    <class name="MyTest1"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <method name="convert"
                     return="java.lang.Double"
                     abstract="false"
                     native="false"
                     synchronized="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <parameter name="null" type="java.lang.Float">
                    </parameter>
                    </method>
                    <field name="ANY_CURSOR_ITEM_TYPE"
                     type="java.lang.String"
                     transient="false"
                     volatile="false"
                     value="&quot;vnd.android.cursor.item/*&quot;"
                     static="true"
                     final="true"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </field>
                    </class>
                    <class name="MyTest2"
                     extends="java.lang.Object"
                     abstract="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <constructor name="MyTest2"
                     type="test.pkg.MyTest2"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </constructor>
                    <method name="convert"
                     return="java.lang.Double"
                     abstract="false"
                     native="false"
                     synchronized="false"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <parameter name="null" type="java.lang.Float">
                    </parameter>
                    </method>
                    </class>
                    </package>
                    <package name="test.pkg.new"
                    >
                    <interface name="MyInterface"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    </interface>
                    <class name="MyTest3"
                     extends="java.lang.Object"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <implements name="java.util.List">
                    </implements>
                    </class>
                    <class name="MyTest4"
                     extends="java.lang.Object"
                     abstract="true"
                     static="false"
                     final="false"
                     deprecated="not deprecated"
                     visibility="public"
                    >
                    <implements name="test.pkg.new.MyInterface">
                    </implements>
                    </class>
                    </package>
                    </api>
                    """
                )
            )
        )
    }

    @Test
    fun `Test convert nothing new`() {
        check(
            expectedOutput = "",
            convertToJDiff = listOf(
                ConvertData(
                    fromApi =
                    """
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        method public java.lang.Double convert(java.lang.Float);
                        field public static final java.lang.String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
                        field public deprecated java.lang.Number myNumber;
                      }
                      public class MyTest2 {
                        ctor public MyTest2();
                        method public java.lang.Double convert(java.lang.Float);
                      }
                    }
                    package test.pkg.new {
                      public class MyTest3 {
                      }
                    }
                    """,
                    baseApi =
                    """
                    package test.pkg {
                      public class MyTest1 {
                        ctor public MyTest1();
                        method public deprecated int clamp(int);
                        method public java.lang.Double convert(java.lang.Float);
                        field public static final java.lang.String ANY_CURSOR_ITEM_TYPE = "vnd.android.cursor.item/*";
                        field public deprecated java.lang.Number myNumber;
                      }
                      public class MyTest2 {
                        ctor public MyTest2();
                        method public java.lang.Double convert(java.lang.Float);
                      }
                    }
                    package test.pkg.new {
                      public class MyTest3 {
                      }
                    }
                    """,
                    outputFile =
                    """
                    <api name="convert-output1" xmlns:metalava="http://www.android.com/metalava/">
                    </api>
                    """
                )
            )
        )
    }
}
