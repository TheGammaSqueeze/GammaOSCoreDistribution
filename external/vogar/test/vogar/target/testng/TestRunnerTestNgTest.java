/*
 * Copyright (C) 2022 The Android Open Source Project
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

package vogar.target.testng;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import vogar.target.AbstractTestRunnerTest;
import vogar.target.TestRunner;
import vogar.target.TestRunnerProperties;

/**
 * Tests for using TestRunner to run TestNG classes.
 */
@RunWith(JUnit4.class)
public class TestRunnerTestNgTest extends AbstractTestRunnerTest {

    @TestRunnerProperties(testClass = ChangeDefaultLocaleTest.class)
    @Test
    public void testRunner_ChangeDefaultLocaleTest() throws Exception {
        TestRunner runner = testRunnerRule.createTestRunner();
        runner.run();

        expectedResults()
                .success("testDefault_Locale_CANADA")
                .success("testDefault_Locale_CHINA")
                .completedNormally();
    }

    @TestRunnerProperties(testClass = SimpleTest.class)
    @Test
    public void testRunner_SkipPastAll() throws Exception {
        Class<?> testClass = testRunnerRule.testClass();
        String failingTestName = testClass.getName() + "#other";
        TestRunner runner = testRunnerRule.createTestRunner("--skipPast", failingTestName);

        runner.run();
        expectedResults().completedNormally();
    }

    @TestRunnerProperties(testClass = SimpleTest.class)
    @Test
    public void testRunner_SimpleTest2_OneMethod() throws Exception {
        String[] args = {"simple2"};
        TestRunner runner = testRunnerRule.createTestRunner(args);
        runner.run();

        expectedResults()
                .success("simple2")
                .completedNormally();
    }

    @TestRunnerProperties(testClass = SimpleTest.class)
    @Test
    public void testRunner_SimpleTest2_TwoMethod() throws Exception {
        String[] args = {"simple1", "Simple3"};
        TestRunner runner = testRunnerRule.createTestRunner(args);
        runner.run();

        expectedResults()
                .success("Simple3")
                .success("simple1")
                .completedNormally();
    }

}
