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

import org.gradle.internal.classpath.DefaultClassPath
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File


class ScriptClasspathStructureRegistryTest {

    private
    fun files(vararg names: String): List<File> = names.map { File(it) }

    @Test
    fun `registered chunks are returned`() {
        val registry = ScriptClasspathStructureRegistry()
        val cp = DefaultClassPath.of(files("a.jar", "b.jar", "c.jar"))

        registry.register(cp, listOf(files("a.jar"), files("b.jar", "c.jar")))

        assertThat(registry.chunksOf(cp.asFiles), equalTo(listOf(files("a.jar"), files("b.jar", "c.jar"))))
    }

    @Test
    fun `registration is rejected when chunks do not reconstruct the classpath`() {
        val registry = ScriptClasspathStructureRegistry()
        val cp = DefaultClassPath.of(files("a.jar", "b.jar"))

        registry.register(cp, listOf(files("a.jar"), files("x.jar")))
        registry.register(cp, listOf(files("a.jar")))
        registry.register(cp, listOf(files("b.jar"), files("a.jar")))

        assertThat(registry.chunksOf(cp.asFiles), nullValue())
    }

    @Test
    fun `empty chunks are dropped`() {
        val registry = ScriptClasspathStructureRegistry()
        val cp = DefaultClassPath.of(files("a.jar"))

        registry.register(cp, listOf(emptyList(), files("a.jar")))

        assertThat(registry.chunksOf(cp.asFiles), equalTo(listOf(files("a.jar"))))
    }

    @Test
    fun `composite registration appends suffix to known prefix structure`() {
        val registry = ScriptClasspathStructureRegistry()
        val prefix = DefaultClassPath.of(files("a.jar", "b.jar"))
        val suffix = DefaultClassPath.of(files("accessors.jar"))
        val combined = DefaultClassPath.of(files("a.jar", "b.jar", "accessors.jar"))
        registry.register(prefix, listOf(files("a.jar"), files("b.jar")))

        registry.registerComposite(combined, suffix)

        assertThat(registry.chunksOf(combined.asFiles), equalTo(listOf(files("a.jar"), files("b.jar"), files("accessors.jar"))))
    }

    @Test
    fun `composite registration is skipped for unknown prefix or mismatched suffix`() {
        val registry = ScriptClasspathStructureRegistry()
        val combined = DefaultClassPath.of(files("a.jar", "accessors.jar"))
        val suffix = DefaultClassPath.of(files("accessors.jar"))
        val wrongSuffix = DefaultClassPath.of(files("other.jar"))

        registry.registerComposite(combined, suffix)

        assertThat(registry.chunksOf(combined.asFiles), nullValue())

        val prefix = DefaultClassPath.of(files("a.jar"))
        registry.register(prefix, listOf(files("a.jar")))
        registry.registerComposite(combined, wrongSuffix)

        assertThat(registry.chunksOf(combined.asFiles), nullValue())
    }

    @Test
    fun `composite registration with empty suffix is a no-op`() {
        val registry = ScriptClasspathStructureRegistry()
        val cp = DefaultClassPath.of(files("a.jar"))
        registry.register(cp, listOf(files("a.jar")))

        registry.registerComposite(cp, org.gradle.internal.classpath.ClassPath.EMPTY)

        assertThat(registry.chunksOf(cp.asFiles), equalTo(listOf(files("a.jar"))))
    }
}
