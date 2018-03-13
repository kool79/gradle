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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.dependency.locking.DependencyLockingDataExchanger.LockfileHandling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.gradle.dependency.locking.exception.LockOutOfDateException.createLockOutOfDateException;
import static org.gradle.dependency.locking.exception.LockOutOfDateException.createLockOutOfDateExceptionStrictMode;

class ConfigurationAfterResolveAction implements Action<ResolvableDependencies> {

    private static final Logger LOGGER = Logging.getLogger(DependencyLockingPlugin.class);

    private final LockfileReader lockfileReader;
    private final DependencyLockingDataExchanger dataExchanger;

    public ConfigurationAfterResolveAction(LockfileReader lockfileReader, DependencyLockingDataExchanger dataExchanger) {
        this.lockfileReader = lockfileReader;
        this.dataExchanger = dataExchanger;
    }

    @Override
    public void execute(ResolvableDependencies resolvableDependencies) {
        String configurationName = resolvableDependencies.getName();
        LOGGER.warn("Post resolve hook for {}", configurationName);
        Map<String, ModuleComponentIdentifier> modules = getMapOfResolvedDependencies(resolvableDependencies);
        LockValidationState validationResult = validateLockAligned(dataExchanger.getLockFileHandling(), dataExchanger.isStrict(), modules, lockfileReader.readLockFile(configurationName));

        dataExchanger.configurationResolved(configurationName, modules, validationResult);
    }

    private LockValidationState validateLockAligned(LockfileHandling lockFileHandling, boolean strict, Map<String, ModuleComponentIdentifier> modules, List<String> lines) {
        LockValidationState state = LockValidationState.VALID;
        if (lines == null) {
            state = LockValidationState.NO_LOCK;
        }
        List<String> errors = new ArrayList<String>();
        Map<String, ModuleComponentIdentifier> extraModules = new HashMap<String, ModuleComponentIdentifier>(modules);
        if (state != LockValidationState.NO_LOCK && lockFileHandling != LockfileHandling.UPDATE_ALL) {
            if (modules.keySet().size() > lines.size()) {
                state = LockValidationState.VALID_APPENDED;
            }
            for (String line : lines) {
                String module = line.substring(0, line.lastIndexOf(':'));
                extraModules.remove(module);
                if (mustCheckModule(module)) {
                    ModuleComponentIdentifier identifier = modules.get(module);
                    if (identifier == null) {
                        errors.add("Lock file contained '" + line + "' but it is not part of the resolved modules");
                    } else if (!line.contains(identifier.getVersion())) {
                        errors.add("Lock file expected '" + line + "' but resolution result was '" + module + ":" + identifier.getVersion() + "'");
                    }
                }
            }
            if (!errors.isEmpty()) {
                state = LockValidationState.INVALID;
            }
        }
        processResult(state, errors, extraModules.values(), strict);
        return state;
    }

    private void processResult(LockValidationState state, List<String> errors, Collection<ModuleComponentIdentifier> extraModules, boolean strict) {
        switch(state) {
            case INVALID:
                throw createLockOutOfDateException(errors);
            case VALID_APPENDED:
                if (strict) {
                    throw createLockOutOfDateExceptionStrictMode(extraModules);
                } else {
                    StringBuilder builder = new StringBuilder("Dependency lock found new modules:\n");
                    for (ModuleComponentIdentifier extraModule : extraModules) {
                        builder.append("\t").append(extraModule.getGroup()).append(":").append(extraModule.getModule()).append(":").append(extraModule.getVersion()).append("\n");
                    }
                    builder.append("\tLock file has been updated with these entries.");
                    LOGGER.lifecycle(builder.toString());
                }
            case VALID:
            case NO_LOCK:
                // Nothing to do
                break;
        }
    }

    private boolean mustCheckModule(String module) {
        return !dataExchanger.getUpgradeModules().contains(module);
    }

    private Map<String, ModuleComponentIdentifier> getMapOfResolvedDependencies(ResolvableDependencies resolvableDependencies) {
        Map<String, ModuleComponentIdentifier> modules = new TreeMap<String, ModuleComponentIdentifier>();
        for (ResolvedComponentResult resolvedComponentResult : resolvableDependencies.getResolutionResult().getAllComponents()) {
            if (resolvedComponentResult.getId() instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier id = (ModuleComponentIdentifier) resolvedComponentResult.getId();
                modules.put(id.getGroup() + ":" + id.getModule(), id);
            }
        }
        LOGGER.warn("Found the following modules:\n\t{}", modules);
        return modules;
    }

    enum LockValidationState {
        VALID,
        INVALID,
        VALID_APPENDED,
        NO_LOCK
    }

}
