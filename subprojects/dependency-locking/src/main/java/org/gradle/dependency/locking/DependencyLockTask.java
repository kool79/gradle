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

package org.gradle.dependency.locking;

import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.util.Arrays;

public class DependencyLockTask extends DefaultTask {

    private static final Logger LOGGER = Logging.getLogger(DependencyLockTask.class);

    private DependencyLockingDataExchanger dataExchanger;

    @Option(option = "upgradeAllLocks", description = "Enables dependency locking upgrade mode, for all modules")
    public void setUpgradeAll(boolean upgradeAll) {
        LOGGER.warn("DependencyLockTask configured with {}", upgradeAll);
        if (upgradeAll) {
            dataExchanger.updateLockFileHandling(DependencyLockingDataExchanger.LockfileHandling.UPDATE_ALL);
        }
    }

    @Option(option = "upgradeModuleLocks", description = "Enables dependency locking upgrade mode, the value of the option are module notations without version, comma separated.")
    public void setUpgradeModules(String modules) {
        LOGGER.warn("DependencyLockTask configured with {}", modules);
        dataExchanger.setUpgradeModules(Arrays.asList(modules.split(",")));
    }

    @TaskAction
    public void triggerLockWriting() {
        LOGGER.warn("DependencyLockTask executing");
        dataExchanger.updateLockFileHandling(DependencyLockingDataExchanger.LockfileHandling.CREATE);
    }

    public void setExchangeProperties(DependencyLockingDataExchanger dataExchanger) {
        this.dataExchanger = dataExchanger;
    }
}
