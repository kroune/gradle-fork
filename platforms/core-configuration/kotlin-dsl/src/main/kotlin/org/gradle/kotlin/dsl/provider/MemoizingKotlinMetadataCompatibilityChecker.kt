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
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.kotlin.dsl.execution.KotlinMetadataCompatibilityChecker
import java.io.File
import java.util.concurrent.ConcurrentHashMap


/**
 * Avoids re-walking whole classpaths for the Kotlin metadata compatibility check:
 * the result is memoized per distinct classpath content, and when the
 * [ScriptClasspathStructureRegistry] knows the chunk decomposition of a classpath,
 * per chunk (chunks shared between classpaths are checked once).
 *
 * Build-scoped: classpaths consumed during a build are immutable while that build runs.
 */
internal
class MemoizingKotlinMetadataCompatibilityChecker(
    private val delegate: KotlinMetadataCompatibilityChecker,
    private val structureRegistry: ScriptClasspathStructureRegistry?,
) : KotlinMetadataCompatibilityChecker {

    private
    val verdictsByContent = ConcurrentHashMap<List<File>, List<File>>()

    private
    val verdictsByChunk = ConcurrentHashMap<List<File>, List<File>>()

    override fun incompatibleClasspathElements(classPath: ClassPath): List<File> {
        val files = classPath.asFiles
        if (files.isEmpty()) {
            return delegate.incompatibleClasspathElements(classPath)
        }
        structureRegistry?.chunksOf(files)?.let { chunks ->
            return chunks.flatMap(::chunkVerdict)
        }
        return verdictsByContent.computeIfAbsent(files) {
            delegate.incompatibleClasspathElements(classPath)
        }
    }

    private
    fun chunkVerdict(chunk: List<File>): List<File> =
        verdictsByChunk.computeIfAbsent(chunk) {
            delegate.incompatibleClasspathElements(DefaultClassPath.of(it))
        }
}
