/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.swift

import org.gradle.language.AbstractNativeLibraryDependenciesIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.SWIFT_SUPPORT)
class SwiftLibraryDependenciesIntegrationTest extends AbstractNativeLibraryDependenciesIntegrationTest {
    @Override
    protected void makeComponentWithLibrary() {
        buildFile << """
            apply plugin: 'swift-library'
            project(':lib') {
                apply plugin: 'swift-library'
            }
        """

        file("src/main/swift/Lib.swift") << librarySource
        file("lib/src/main/swift/Lib.swift") << librarySource
    }

    @Override
    protected void makeComponentWithIncludedBuildLibrary() {
        buildFile << """
            apply plugin: 'swift-library'
        """

        file('lib/build.gradle') << """
            apply plugin: 'swift-library'
            
            group = 'org.gradle.test'
            version = '1.0'
        """
        file('lib/settings.gradle').createFile()

        file("src/main/swift/Lib.swift") << librarySource
        file("lib/src/main/swift/Lib.swift") << librarySource
    }

    private static String getLibrarySource() {
        return """
            class Lib {
            }
        """
    }

    @Override
    protected String getComponentUnderTestDsl() {
        return "library"
    }

    @Override
    protected List<String> getAssembleDebugTasks() {
        return [":compileDebugSwift", ":linkDebug"]
    }

    @Override
    protected List<String> getAssembleReleaseTasks() {
        return [":compileReleaseSwift", ":linkRelease", ":stripSymbolsRelease", ":extractSymbolsRelease"]
    }

    @Override
    protected List<String> getLibDebugTasks() {
        return [":lib:compileDebugSwift", ":lib:linkDebug"]
    }
}