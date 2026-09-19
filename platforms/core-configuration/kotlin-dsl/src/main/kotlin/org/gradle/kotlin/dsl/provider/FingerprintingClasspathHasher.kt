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

import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import java.io.File
import java.util.concurrent.ConcurrentHashMap


/**
 * A [ClasspathHasher] that avoids re-fingerprinting whole classpaths:
 *
 *  - when the [ScriptClasspathStructureRegistry] knows the chunk decomposition of a
 *    classpath, each chunk is fingerprinted once per build and the chunk fingerprints
 *    are folded in classpath order;
 *  - otherwise the full fingerprint is computed by [delegate] once per distinct
 *    classpath content.
 *
 * Note that chunk-folded fingerprints intentionally differ from delegate-computed ones
 * (chunk grouping changes the fold). They remain deterministic and content-sensitive,
 * but enabling chunking is a one-time identity change for persistent script
 * compilation workspaces.
 */
internal
class FingerprintingClasspathHasher(
    private val delegate: ClasspathHasher,
    private val structureRegistry: ScriptClasspathStructureRegistry? = null,
) : ClasspathHasher {

    private
    val hashesByContent = ConcurrentHashMap<List<File>, HashCode>()

    private
    val hashesByChunk = ConcurrentHashMap<List<File>, HashCode>()

    override fun hash(classpath: ClassPath): HashCode {
        val files = classpath.asFiles
        if (files.isEmpty()) {
            return delegate.hash(classpath)
        }
        structureRegistry?.chunksOf(files)?.let { chunks ->
            return hashChunks(chunks)
        }
        return hashesByContent.computeIfAbsent(files) { delegate.hash(classpath) }
    }

    private
    fun hashChunks(chunks: List<List<File>>): HashCode {
        val hasher = Hashing.newHasher()
        chunks.forEach { chunk ->
            hasher.putHash(chunkHash(chunk))
        }
        return hasher.hash()
    }

    private
    fun chunkHash(chunk: List<File>): HashCode =
        hashesByChunk.computeIfAbsent(chunk) {
            delegate.hash(DefaultClassPath.of(it))
        }
}
