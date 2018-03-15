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

import com.google.common.base.Objects;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.internal.file.FileType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultSourceIncludesSearchPath implements SourceIncludesSearchPath {
    private final List<File> includePaths;
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final Map<File, Map<String, IncludeFileImpl>> includeRoots;

    public DefaultSourceIncludesSearchPath(List<File> includePaths, FileSystemSnapshotter fileSystemSnapshotter) {
        this.includePaths = includePaths;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.includeRoots = new HashMap<File, Map<String, IncludeFileImpl>>();
    }

    @Override
    public SourceIncludesSearchPath asQuotedSearchPath(File sourceFile) {
        final List<File> includePaths = prependSourceDir(sourceFile, this.includePaths);
        return new SourceIncludesSearchPath() {
            @Override
            public SourceIncludesSearchPath asQuotedSearchPath(File sourceFile) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void searchForDependency(String include, SearchResult dependencies) {
                DefaultSourceIncludesSearchPath.this.searchForDependency(includePaths, include, dependencies, true);
            }
        };
    }

    private List<File> prependSourceDir(File sourceFile, List<File> includePaths) {
        File sourceDir = sourceFile.getParentFile();
        if (includePaths.size() > 1 && includePaths.get(0).equals(sourceDir)) {
            // Source dir already at the start of the path, just use the include path
            return includePaths;
        }
        List<File> quotedSearchPath = new ArrayList<File>(includePaths.size() + 1);
        quotedSearchPath.add(sourceDir);
        quotedSearchPath.addAll(includePaths);
        return quotedSearchPath;
    }

    @Override
    public void searchForDependency(String include, SearchResult dependencies) {
        searchForDependency(includePaths, include, dependencies, false);
    }

    private void searchForDependency(List<File> searchPath, String include, SearchResult dependencies, boolean quoted) {
        for (File searchDir : searchPath) {
            Map<String, IncludeFileImpl> searchedIncludes = includeRoots.get(searchDir);
            if (searchedIncludes == null) {
                searchedIncludes = new HashMap<String, IncludeFileImpl>();
                includeRoots.put(searchDir, searchedIncludes);
            }
            if (searchedIncludes.containsKey(include)) {
                IncludeFileImpl includeFile = searchedIncludes.get(include);
                if (includeFile.snapshot.getType() == FileType.RegularFile) {
                    dependencies.resolved(includeFile);
                    return;
                }
                continue;
            }

            File candidate = new File(searchDir, include);
            FileSnapshot fileSnapshot = fileSystemSnapshotter.snapshotSelf(candidate);
            IncludeFileImpl includeFile = fileSnapshot.getType() == FileType.RegularFile ? new IncludeFileImpl(candidate, fileSnapshot, include, quoted) : new IncludeFileImpl(null, fileSnapshot, include, quoted);
            searchedIncludes.put(include, includeFile);

            if (fileSnapshot.getType() == FileType.RegularFile) {
                dependencies.resolved(includeFile);
                return;
            }
        }
    }

    private static class IncludeFileImpl implements SourceIncludesResolver.IncludeFile {
        final File file;
        final FileSnapshot snapshot;
        private final String include;
        private final boolean quotedInclude;

        IncludeFileImpl(File file, FileSnapshot snapshot, String include, boolean quotedInclude) {
            this.file = file;
            this.snapshot = snapshot;
            this.include = include;
            this.quotedInclude = quotedInclude;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public FileSnapshot getSnapshot() {
            return snapshot;
        }

        @Override
        public String getInclude() {
            return include;
        }

        @Override
        public boolean isQuotedInclude() {
            return quotedInclude;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            IncludeFileImpl other = (IncludeFileImpl) obj;
            return Objects.equal(file, other.file) && snapshot.equals(other.snapshot);
        }

        @Override
        public int hashCode() {
            return snapshot.hashCode();
        }
    }
}
