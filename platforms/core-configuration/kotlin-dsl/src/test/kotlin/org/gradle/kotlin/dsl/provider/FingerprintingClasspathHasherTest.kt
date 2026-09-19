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
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap


class FingerprintingClasspathHasherTest {

    private
    class CountingClasspathHasher : ClasspathHasher {
        val hashesByContent = ConcurrentHashMap<List<File>, HashCode>()
        val calls = ConcurrentHashMap<List<File>, Int>()

        override fun hash(classpath: ClassPath): HashCode {
            val files = classpath.asFiles
            calls.merge(files, 1, Int::plus)
            return hashesByContent.computeIfAbsent(files) {
                Hashing.newHasher().apply { putString(files.joinToString("\n") { it.path }) }.hash()
            }
        }

        fun callsFor(files: List<File>): Int = calls[files] ?: 0
    }

    private
    fun files(vararg names: String): List<File> = names.map { File(it) }

    private
    fun classPathOf(files: List<File>): ClassPath = DefaultClassPath.of(files)

    @Test
    fun `content mode memoizes per classpath content`() {
        val delegate = CountingClasspathHasher()
        val hasher = FingerprintingClasspathHasher(delegate)
        val files = files("a.jar", "b.jar")

        val first = hasher.hash(classPathOf(files))
        val second = hasher.hash(classPathOf(files))

        assertThat(second, equalTo(first))
        assertThat(delegate.callsFor(files), equalTo(1))
        assertThat(first, equalTo(delegate.hash(classPathOf(files))))
    }

    @Test
    fun `content mode distinguishes order`() {
        val delegate = CountingClasspathHasher()
        val hasher = FingerprintingClasspathHasher(delegate)

        val ab = hasher.hash(classPathOf(files("a.jar", "b.jar")))
        val ba = hasher.hash(classPathOf(files("b.jar", "a.jar")))

        assertThat(ab, not(equalTo(ba)))
    }

    @Test
    fun `chunked mode fingerprints shared chunks only once`() {
        val delegate = CountingClasspathHasher()
        val registry = ScriptClasspathStructureRegistry()
        val chunkA = files("gradle-api.jar")
        val chunkB = files("plugin-b.jar")
        val chunkC = files("plugin-c.jar")
        val cpAB = classPathOf(chunkA + chunkB)
        val cpAC = classPathOf(chunkA + chunkC)
        registry.register(cpAB, listOf(chunkA, chunkB))
        registry.register(cpAC, listOf(chunkA, chunkC))

        val hasher = FingerprintingClasspathHasher(delegate, registry)
        val hashAB = hasher.hash(cpAB)
        val hashAC = hasher.hash(cpAC)

        assertThat(hashAB, not(equalTo(hashAC)))
        assertThat(delegate.callsFor(chunkA), equalTo(1))
        assertThat(delegate.callsFor(chunkB), equalTo(1))
        assertThat(delegate.callsFor(chunkC), equalTo(1))
        // the whole classpaths are never fingerprinted directly
        assertThat(delegate.callsFor(chunkA + chunkB), equalTo(0))
        assertThat(delegate.callsFor(chunkA + chunkC), equalTo(0))
    }

    @Test
    fun `chunked mode is deterministic and order sensitive`() {
        val delegate = CountingClasspathHasher()
        val chunkA = files("a.jar")
        val chunkB = files("b.jar")

        val registry1 = ScriptClasspathStructureRegistry()
        val cpAB = classPathOf(chunkA + chunkB)
        val cpBA = classPathOf(chunkB + chunkA)
        registry1.register(cpAB, listOf(chunkA, chunkB))
        registry1.register(cpBA, listOf(chunkB, chunkA))
        val hashAB = FingerprintingClasspathHasher(delegate, registry1).hash(cpAB)
        val hashBA = FingerprintingClasspathHasher(delegate, registry1).hash(cpBA)

        val registry2 = ScriptClasspathStructureRegistry()
        registry2.register(cpAB, listOf(chunkA, chunkB))
        val hashABAgain = FingerprintingClasspathHasher(CountingClasspathHasher(), registry2).hash(cpAB)

        assertThat(hashABAgain, equalTo(hashAB))
        assertThat(hashAB, not(equalTo(hashBA)))
    }

    @Test
    fun `chunked mode falls back to content memoization for unknown classpaths`() {
        val delegate = CountingClasspathHasher()
        val registry = ScriptClasspathStructureRegistry()
        val hasher = FingerprintingClasspathHasher(delegate, registry)
        val files = files("unknown.jar")

        val first = hasher.hash(classPathOf(files))
        val second = hasher.hash(classPathOf(files))

        assertThat(second, equalTo(first))
        assertThat(delegate.callsFor(files), equalTo(1))
    }

    @Test
    fun `empty classpath is delegated`() {
        val delegate = CountingClasspathHasher()
        val hasher = FingerprintingClasspathHasher(delegate, ScriptClasspathStructureRegistry())

        hasher.hash(ClassPath.EMPTY)

        assertThat(delegate.callsFor(emptyList()), equalTo(1))
    }
}
