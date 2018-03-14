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

package org.gradle.language.nativeplatform.internal.incremental;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import java.io.File;
import java.util.Collection;

public class IncrementalCompileSourceProcessorCache {
    private final Multimap<File, IncrementalCompileFilesFactory.FileDetails> cache = MultimapBuilder.ListMultimapBuilder.hashKeys().arrayListValues().build();
    private final Object lock = new Object();

    public Collection<IncrementalCompileFilesFactory.FileDetails> get(File file) {
        synchronized (lock) {
            return cache.get(file);
        }
    }

    public void put(File file, IncrementalCompileFilesFactory.FileDetails fileDetails) {
        synchronized (lock) {
            cache.put(file, fileDetails);
        }
    }
}
