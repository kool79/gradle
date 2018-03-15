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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

class DependencyLockingDataExchanger {

    enum LockfileHandling {
        VALIDATE,
        CREATE,
        UPDATE_ALL
    }

    private final AtomicReference<LockfileHandling> lockfileHandling = new AtomicReference<LockfileHandling>(LockfileHandling.VALIDATE);
    private final AtomicReference<List<String>> upgradeModules = new AtomicReference<List<String>>(Collections.<String>emptyList());
    private final Map<String, Map<String, ModuleComponentIdentifier>> resolvedConfigurations = new ConcurrentHashMap<String, Map<String, ModuleComponentIdentifier>>();
    private LockfileWriter lockfileWriter;

    public void updateLockFileHandling(LockfileHandling updated) {
        if (lockfileHandling.compareAndSet(LockfileHandling.VALIDATE, updated)) {
            writePreviouslyResolvedConfigurations();
        }
    }

    private synchronized void writePreviouslyResolvedConfigurations() {
        for (Map.Entry<String, Map<String, ModuleComponentIdentifier>> resolvedConfiguration : resolvedConfigurations.entrySet()) {
            lockfileWriter.writeLockFile(resolvedConfiguration.getKey(), resolvedConfiguration.getValue());
        }
    }

    public LockfileHandling getLockFileHandling() {
        return lockfileHandling.get();
    }

    public void setUpgradeModules(List<String> modules) {
        upgradeModules.set(modules);
    }

    public List<String> getUpgradeModules() {
        return upgradeModules.get();
    }

    public void configurationResolved(String configurationName, Map<String, ModuleComponentIdentifier> modules, ConfigurationAfterResolveAction.LockValidationState validationResult) {
        if (lockfileHandling.get() != LockfileHandling.VALIDATE || validationResult == ConfigurationAfterResolveAction.LockValidationState.VALID_APPENDED) {
            lockfileWriter.writeLockFile(configurationName, modules);
        } else {
            resolvedConfigurations.put(configurationName, modules);
            if (lockfileHandling.get() != LockfileHandling.VALIDATE) {
                writePreviouslyResolvedConfigurations();
            }
        }
    }

    public void setLockfileWriter(LockfileWriter lockfileWriter) {
        this.lockfileWriter = lockfileWriter;
    }

    public boolean isStrict() {
        // This is currently not a configuration option
        return true;
    }

}
