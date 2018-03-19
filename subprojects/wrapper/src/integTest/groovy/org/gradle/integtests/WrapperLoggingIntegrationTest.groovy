/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.executer.ExecutionFailure

class WrapperLoggingIntegrationTest extends AbstractWrapperIntegrationSpec {
    def setup() {
        file("build.gradle") << "task emptyTask"
    }

    def "wrapper does not output anything when executed in quiet mode"() {
        given:
        prepareWrapper()

        when:
        args '-q'
        def result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        result.output.empty
    }

    def "wrapper logs when there is a problem setting permissions"() {
        given: "malformed distribution"
        // Repackage distribution with bin/gradle removed so permissions cannot be set
        def tempUnzipDir = temporaryFolder.createDir("temp-unzip")
        distribution.binDistribution.unzipTo(tempUnzipDir)
        distribution.binDistribution.delete()
        assert tempUnzipDir.file("gradle-${distribution.version.version}", "bin", "gradle").delete()
        tempUnzipDir.zipTo(distribution.binDistribution)
        prepareWrapper(distribution.binDistribution.toURI())

        when:
        def failure = wrapperExecuter
            .withTasks("emptyTask")
            .withStackTraceChecksDisabled()
            .run()

        then:
        failure.output.contains("Could not set executable permissions")
    }

    def "wrapper logs when unzipping distribution fails"() {
        given: "Empty zip file"
        prepareWrapper(distribution.binDistribution.toURI())
        distribution.binDistribution.text = ""

        when:
        ExecutionFailure failure = wrapperExecuter
            .withTasks("emptyTask")
            .withStackTraceChecksDisabled()
            .runWithFailure()

        then:
        failure.output.contains("Could not unzip")
    }
}
