/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import groovy.util.logging.Log
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.measure.MeasuredOperation

@CompileStatic
@Log
class JfrProfiler implements Profiler {
    private static final String TARGET_DIR_KEY = "org.gradle.performance.flameGraphTargetDir"

    final boolean enabled

    private final boolean canGenerateFlameGraphs
    private final File logDirectory
    private final File jcmd

    String sessionId
    boolean usesDaemon
    private boolean running

    JfrProfiler() {
        enabled = System.getProperty(TARGET_DIR_KEY) != null
        logDirectory = enabled ? new File(System.getProperty(TARGET_DIR_KEY)) : null
        canGenerateFlameGraphs = locateFlameGraphInstallation().exists()
        this.jcmd = findJcmd()
    }

    private static File findJcmd() {
        File javaHome = new File(System.getProperty("java.home"));
        def jcmdPath = "bin/" + OperatingSystem.current().getExecutableName("jcmd")
        def jcmd = new File(javaHome, jcmdPath);
        if (!jcmd.isFile() && javaHome.getName().equals("jre")) {
            jcmd = new File(javaHome.getParentFile(), jcmdPath);
        }
        if (!jcmd.isFile()) {
            throw new RuntimeException("Could not find 'jcmd' executable for Java home directory " + javaHome);
        }
        jcmd
    }

    @Override
    List<String> getAdditionalJvmOpts(File workingDir) {
        if (!enabled) {
            return Collections.emptyList()
        }
        String flightRecordOptions = "stackdepth=1024"
        if (!usesDaemon) {
            flightRecordOptions += ",defaultRecording=true,dumponexit=true,dumponexitpath=$destFile,settings=profile"
        }
        ["-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", "-XX:FlightRecorderOptions=$flightRecordOptions", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"] as List<String>
    }

    private static File locateFlameGraphInstallation() {
        new File(System.getenv("FG_HOME_DIR") ?: "${System.getProperty('user.home')}/tools/FlameGraph".toString())
    }

    private static File locateJfrFlameGraphInstallation() {
        new File(System.getenv("JFR_FG_HOME_DIR") ?: "${System.getProperty('user.home')}/tools/jfr-flameGraph".toString())
    }

    @Override
    List<String> getAdditionalArgs(File workingDir) {
        return Collections.emptyList();
    }

    @Override
    void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        if (!enabled) {
            return
        }
        if (invocationInfo.iterationNumber == invocationInfo.iterationMax && invocationInfo.phase == BuildExperimentRunner.Phase.MEASUREMENT) {
            if (usesDaemon) {
                dump()
            }
            if (canGenerateFlameGraphs) {
                def stacksFile = new File(logDirectory, "${sessionId}-stacks.txt")
                collapseStacks(destFile, stacksFile)
                def sanitizedStacksFile = new File(logDirectory, "${stacksFile.name}.sanitized")
                sanitizeStacks(stacksFile, sanitizedStacksFile)
                def svgDestFile = new File(logDirectory, "${sessionId}-flames.svg")
                generateFlameGraph(sanitizedStacksFile, svgDestFile)
            }
        }
    }

    private File getDestFile() {
        new File(logDirectory, "${sessionId}.jfr")
    }


    private static void collapseStacks(File recording, File stacks) {

    }

    private static void sanitizeStacks(File stacks, File sanitizedStacks) {
        FlameGraphSanitizer flameGraphSanitizer = new FlameGraphSanitizer(new FlameGraphSanitizer.RegexBasedSanitizerFunction(
            (~'build_([a-z0-9]+)'): 'build script',
            (~'settings_([a-z0-9]+)'): 'settings script',
            (~'org[.]gradle[.]'): '',
            (~'sun[.]reflect[.]GeneratedMethodAccessor[0-9]+'): 'GeneratedMethodAccessor'
        ))
        flameGraphSanitizer.sanitize(stacks, sanitizedStacks)
    }

    private static void generateFlameGraph(File sanitizedOutput, File svgDestFile) {
        File flameGraphHomeDir = locateFlameGraphInstallation()
        def process = ["$flameGraphHomeDir/flamegraph.pl", "--minwidth", "0.5", sanitizedOutput].execute()
        def fos = svgDestFile.newOutputStream()
        process.waitForProcessOutput(fos, System.err)
        fos.close()
    }

    void start() {
        if (enabled && usesDaemon && !running) {
            jcmd("JFR.start", "settings=profile")
        }
    }

    void stop() {
        if (enabled && usesDaemon && running) {
            jcmd("JFR.stop")
        }
    }

    private void dump() {
        jcmd("JFR.stop", "fileName=${destFile}")
    }

    private void jcmd(String... args) {
        def processArguments = [jcmd.absolutePath] + args.toList()
        def process = processArguments.execute()
        process.waitForProcessOutput(System.out as Appendable, System.err as Appendable)
    }
}
