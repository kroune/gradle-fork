/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.provider

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap


/**
 * Records how Kotlin script compilation classpaths are composed of reusable chunks
 * (the Gradle Kotlin DSL ABI classpath, the exported classpath of each class loader
 * in a scope hierarchy, per-project accessor jars).
 *
 * Two classpaths that differ only in some of their chunks (e.g. projects applying
 * different plugin combinations) share the chunks they have in common, so consumers
 * can fingerprint each chunk once instead of re-walking the whole classpath.
 *
 * Build-scoped: classpaths consumed during a build are immutable while that build runs.
 */
@ServiceScope(Scope.Build::class)
class ScriptClasspathStructureRegistry {

    private
    val structures = ConcurrentHashMap<List<File>, List<List<File>>>()

    /**
     * Registers [classPath] as the concatenation of [chunks].
     *
     * Registration is skipped unless the chunks reconstruct the classpath exactly,
     * so consumers never observe a decomposition that doesn't match the content.
     */
    fun register(classPath: ClassPath, chunks: List<List<File>>) {
        val files = classPath.asFiles
        if (!reconstructs(files, chunks)) {
            return
        }
        structures.putIfAbsent(files, chunks.filter { it.isNotEmpty() })
    }

    /**
     * Registers [combined] as `structure(prefix) + [suffix]` when [combined] is exactly
     * `prefix + suffix` and the structure of `prefix` is already known.
     *
     * Used for `compilationClassPath + accessorsClassPath` combinations.
     */
    fun registerComposite(combined: ClassPath, suffix: ClassPath) {
        val suffixFiles = suffix.asFiles
        if (suffixFiles.isEmpty()) {
            return
        }
        val combinedFiles = combined.asFiles
        if (combinedFiles.size <= suffixFiles.size || combinedFiles.subList(combinedFiles.size - suffixFiles.size, combinedFiles.size) != suffixFiles) {
            return
        }
        val prefixFiles = combinedFiles.subList(0, combinedFiles.size - suffixFiles.size)
        val prefixChunks = structures[prefixFiles] ?: return
        structures.putIfAbsent(combinedFiles, prefixChunks + listOf(suffixFiles))
    }

    /**
     * The registered chunk decomposition of the classpath with the given content,
     * or `null` when unknown. Chunks are non-empty and concatenate to the given files.
     */
    fun chunksOf(files: List<File>): List<List<File>>? =
        structures[files]

    private
    fun reconstructs(files: List<File>, chunks: List<List<File>>): Boolean {
        var index = 0
        for (chunk in chunks) {
            for (file in chunk) {
                if (index >= files.size || files[index] != file) {
                    return false
                }
                index++
            }
        }
        return index == files.size
    }
}
