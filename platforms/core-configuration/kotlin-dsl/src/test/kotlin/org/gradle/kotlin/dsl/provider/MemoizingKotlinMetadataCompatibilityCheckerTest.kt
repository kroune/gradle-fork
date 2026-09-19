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
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap


class MemoizingKotlinMetadataCompatibilityCheckerTest {

    private
    class CountingChecker(
        private val incompatible: Set<File>
    ) : KotlinMetadataCompatibilityChecker {
        val calls = ConcurrentHashMap<List<File>, Int>()

        override fun incompatibleClasspathElements(classPath: ClassPath): List<File> {
            val files = classPath.asFiles
            calls.merge(files, 1, Int::plus)
            return files.filter { it in incompatible }
        }

        fun callsFor(files: List<File>): Int = calls[files] ?: 0
    }

    private
    fun files(vararg names: String): List<File> = names.map { File(it) }

    @Test
    fun `verdict is memoized per classpath content`() {
        val delegate = CountingChecker(setOf(File("bad.jar")))
        val checker = MemoizingKotlinMetadataCompatibilityChecker(delegate, null)
        val cp = DefaultClassPath.of(files("bad.jar", "good.jar"))

        val first = checker.incompatibleClasspathElements(cp)
        val second = checker.incompatibleClasspathElements(DefaultClassPath.of(files("bad.jar", "good.jar")))

        assertThat(first, equalTo(files("bad.jar")))
        assertThat(second, equalTo(files("bad.jar")))
        assertThat(delegate.callsFor(files("bad.jar", "good.jar")), equalTo(1))
    }

    @Test
    fun `chunked mode checks shared chunks only once and concatenates verdicts in order`() {
        val delegate = CountingChecker(setOf(File("bad-b.jar"), File("bad-c.jar")))
        val registry = ScriptClasspathStructureRegistry()
        val chunkA = files("gradle-api.jar")
        val chunkB = files("bad-b.jar")
        val chunkC = files("bad-c.jar")
        val cpAB = DefaultClassPath.of(chunkA + chunkB)
        val cpAC = DefaultClassPath.of(chunkA + chunkC)
        registry.register(cpAB, listOf(chunkA, chunkB))
        registry.register(cpAC, listOf(chunkA, chunkC))
        val checker = MemoizingKotlinMetadataCompatibilityChecker(delegate, registry)

        val verdictAB = checker.incompatibleClasspathElements(cpAB)
        val verdictAC = checker.incompatibleClasspathElements(cpAC)

        assertThat(verdictAB, equalTo(files("bad-b.jar")))
        assertThat(verdictAC, equalTo(files("bad-c.jar")))
        assertThat(delegate.callsFor(chunkA), equalTo(1))
        assertThat(delegate.callsFor(chunkB), equalTo(1))
        assertThat(delegate.callsFor(chunkC), equalTo(1))
    }
}
