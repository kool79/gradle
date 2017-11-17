/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.hamcrest.Matchers
import org.junit.Assume
import spock.lang.Unroll

abstract class AbstractTestFrameworkIntegrationTest extends AbstractIntegrationSpec {
    abstract void createPassingFailingTest()
    abstract void createEmptyProject()
    abstract void renameTests()

    abstract String getTestTaskName()

    abstract String getPassingTestCaseName()
    abstract String getFailingTestCaseName()

    String testSuite(String testSuite) {
        return testSuite
    }

    def "can listen for test results"() {
        given:
        createPassingFailingTest()

        buildFile << """
            def listener = new TestListenerImpl()
            ${testTaskName}.addTestListener(listener)
            ${testTaskName}.ignoreFailures = true
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START Test Suite [\$suite.className] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH Test Suite [\$suite.className] [\$suite.name] [\$result.resultType] [\$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START Test Case [\$test.className] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH Test Case [\$test.className] [\$test.name] [\$result.resultType] [\$result.testCount]" }
            }
        """

        when:
        succeeds "check"

        then:

        outputContains "START Test Suite [null] [Gradle Test Run :$testTaskName]"
        outputContains "FINISH Test Suite [null] [Gradle Test Run :$testTaskName] [FAILURE] [3]"

        outputContains "START Test Suite [SomeOtherTest] [SomeOtherTest]"
        outputContains "FINISH Test Suite [SomeOtherTest] [SomeOtherTest]"
        outputContains "START Test Case [SomeOtherTest] [$passingTestCaseName]"
        outputContains "FINISH Test Case [SomeOtherTest] [$passingTestCaseName] [SUCCESS] [1]"

        outputContains "START Test Suite [SomeTest] [SomeTest]"
        outputContains "FINISH Test Suite [SomeTest] [SomeTest]"
        outputContains "START Test Case [SomeTest] [$failingTestCaseName]"
        outputContains "FINISH Test Case [SomeTest] [$failingTestCaseName] [FAILURE] [1]"
        outputContains "START Test Case [SomeTest] [$passingTestCaseName]"
        outputContains "FINISH Test Case [SomeTest] [$passingTestCaseName] [SUCCESS] [1]"
    }

    def "test results conventions are consistent"() {
        given:
        createPassingFailingTest()

        buildFile << """
            task verifyTestResultConventions {
                doLast {
                    assert ${testTaskName}.reports.junitXml.destination == file('build/test-results/${testTaskName}')
                    assert ${testTaskName}.reports.html.destination == file('build/reports/tests/${testTaskName}')
                    assert ${testTaskName}.binResultsDir == file('build/test-results/${testTaskName}/binary')
                }
            }
        """

        expect:
        succeeds "verifyTestResultConventions"
    }

    def "test results show passing and failing tests"() {
        given:
        createPassingFailingTest()

        buildFile << """
            ${testTaskName}.ignoreFailures = true
        """

        when:
        succeeds "check"

        then:
        testResult.assertTestClassesExecuted('SomeTest', 'SomeOtherTest')
        testResult.testClass('SomeTest').assertTestFailed(failingTestCaseName, Matchers.containsString("test failure message"))
        testResult.testClass('SomeOtherTest').assertTestPassed(passingTestCaseName)
    }

    def "test results capture test output"() {
        Assume.assumeTrue(capturesTestOutput())
        given:
        createPassingFailingTest()

        buildFile << """
            ${testTaskName}.ignoreFailures = true
        """

        when:
        succeeds "check"

        then:
        testResult.testClass('SomeTest').assertStderr(Matchers.containsString("some error output"))
    }

    def "failing tests cause report url to be printed"() {
        given:
        createPassingFailingTest()

        when:
        fails "check"

        then:
        errorOutput.contains("There were failing tests. See the report at:")
    }

    def "lack of tests produce an empty report"() {
        given:
        createEmptyProject()

        when:
        succeeds "check"

        then:
        testResult.assertNoTestClassesExecuted()
    }

    def "adding and removing tests remove old tests from reports"() {
        given:
        createPassingFailingTest()
        fails("check")
        when:
        renameTests()
        fails("check")
        then:
        testResult.assertTestClassesExecuted('SomeTest', 'NewTest')
    }

    def "honors test case filter from --tests flag"() {
        given:
        createPassingFailingTest()

        when:
        run testTaskName, '--tests', "${testSuite('SomeOtherTest')}.$passingTestCaseName"

        then:
        testResult.assertTestClassesExecuted('SomeOtherTest')
        testResult.testClass('SomeOtherTest').assertTestPassed(passingTestCaseName)
    }

    def "honors test suite filter from --tests flag"() {
        given:
        createPassingFailingTest()

        when:
        run testTaskName, '--tests', "${testSuite('SomeOtherTest')}.*"

        then:
        testResult.assertTestClassesExecuted('SomeOtherTest')
        testResult.testClass('SomeOtherTest').assertTestPassed(passingTestCaseName)
    }

    def "reports when no matching methods found"() {
        given:
        createPassingFailingTest()

        //by command line
        when: fails(testTaskName, "--tests", "${testSuite('SomeTest')}.missingMethod")
        then: failure.assertHasCause("No tests found for given includes: [${testSuite('SomeTest')}.missingMethod](--tests filter)")

        //by build script
        when:
        buildFile << "${testTaskName}.filter.includeTestsMatching '${testSuite('SomeTest')}.missingMethod'"
        fails(testTaskName)
        then: failure.assertHasCause("No tests found for given includes: [${testSuite('SomeTest')}.missingMethod](filter.includeTestsMatching)")
    }

    def "task is out of date when --tests argument changes"() {
        String testTaskPath = ":$testTaskName"

        given:
        createPassingFailingTest()

        when: run(testTaskName, "--tests", "${testSuite('SomeOtherTest')}.$passingTestCaseName")
        then: testResult.testClass("SomeOtherTest").assertTestsExecuted(passingTestCaseName)

        when: run(testTaskName, "--tests", "${testSuite('SomeOtherTest')}.$passingTestCaseName")
        then: result.skippedTasks.contains(testTaskPath) //up-to-date

        when:
        run(testTaskName, "--tests", "${testSuite('SomeTest')}.$passingTestCaseName")

        then:
        !result.skippedTasks.contains(testTaskPath)
        testResult.testClass("SomeTest").assertTestsExecuted(passingTestCaseName)
    }

    @Unroll
    def "can select multiple tests from commandline #scenario"() {
        given:
        createPassingFailingTest()

        when:
        runAndFail(stringArrayOf(command))

        then:
        testResult.assertTestClassesExecuted(stringArrayOf(classesExecuted))
        if (!someOtherTestExecuted.isEmpty()) {
            testResult.testClass("SomeOtherTest").assertTestsExecuted(stringArrayOf(someOtherTestExecuted))
        }
        testResult.testClass("SomeTest").assertTestsExecuted(stringArrayOf(someTestExecuted))

        where:
        scenario                      | command                                                                                                                                                       | classesExecuted               | someOtherTestExecuted | someTestExecuted
        "no options"                  | [testTaskName]                                                                                                                                                | ["SomeTest", "SomeOtherTest"] | [passingTestCaseName] | [passingTestCaseName, failingTestCaseName]
        "fail and SomeTest.pass"      | [testTaskName, "--tests", "${testSuite('SomeTest')}.$failingTestCaseName", "--tests", "${testSuite('SomeTest')}.$passingTestCaseName"]      | ["SomeTest"]                  | []                    | [passingTestCaseName, failingTestCaseName]
        "fail and SomeOtherTest.fail" | [testTaskName, "--tests", "${testSuite('SomeTest')}.$failingTestCaseName", "--tests", "${testSuite('SomeOtherTest')}.$passingTestCaseName"] | ["SomeTest", "SomeOtherTest"] | [passingTestCaseName] | [failingTestCaseName]
    }

    @Unroll
    def "can deduplicate test filters when #scenario"() {
        given:
        createPassingFailingTest()

        when:
        runAndFail(stringArrayOf(command))

        then:
        testResult.assertTestClassesExecuted('SomeTest')
        testResult.testClass("SomeTest").assertTestsExecuted(passingTestCaseName, failingTestCaseName)

        where:
        scenario                             | command
        "test suite appear before test case" | [testTaskName, "--tests", "${testSuite('SomeTest')}.*", "--tests", "${testSuite('SomeTest')}.$passingTestCaseName"]
        "test suite appear after test case"  | [testTaskName, "--tests", "${testSuite('SomeTest')}.$passingTestCaseName", "--tests", "${testSuite('SomeTest')}.*"]
    }

    private DefaultTestExecutionResult getTestResult() {
        new DefaultTestExecutionResult(testDirectory, 'build', '', '', testTaskName)
    }

    protected boolean capturesTestOutput() {
        return true
    }

    private String[] stringArrayOf(List<String> strings) {
        return strings.toArray()
    }
}
