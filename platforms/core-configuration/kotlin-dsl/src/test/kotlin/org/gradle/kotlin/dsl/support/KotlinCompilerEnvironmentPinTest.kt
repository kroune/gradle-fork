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

package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.number.OrderingComparison.greaterThan
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.jarfs.FastJarFileSystem
import org.junit.After
import org.junit.Assume.assumeThat
import org.junit.Test
import org.mockito.kotlin.mock


open class ThePinnedScriptTemplate(
    @Suppress("unused_parameter") host: Any
)


@OptIn(K1Deprecation::class)
class KotlinCompilerEnvironmentPinTest : TestWithTempFiles() {

    @After
    fun disposeCompilerContext() {
        disposeKotlinCompilerContext()
    }

    @Test
    fun `jar caches are dropped after compilation when no environment is pinned`() {
        compileScript()

        // documents the failure mode the pin prevents:
        // the compiler treats the post-compilation idle moment as a cue to drop all jar caches
        assertThat(jarFileSystemHandlerCount(), equalTo(0))
    }

    @Test
    fun `jar caches survive compilation when an environment is pinned`() {
        val pin = pinKotlinCompilerEnvironment()
        try {
            compileScript()

            assertThat(jarFileSystemHandlerCount(), greaterThan(0))
        } finally {
            unpinKotlinCompilerEnvironment(pin)
        }
    }

    @Test
    fun `jar caches are dropped once the pin is released`() {
        val pin = pinKotlinCompilerEnvironment()
        compileScript()

        unpinKotlinCompilerEnvironment(pin)

        assertThat(jarFileSystemHandlerCount(), equalTo(0))
    }

    private
    fun jarFileSystemHandlerCount(): Int {
        val fileSystem = KotlinCoreEnvironment.applicationEnvironment?.fastJarFileSystem
        assumeThat(fileSystem, notNullValue())
        val handlersField = FastJarFileSystem::class.java.getDeclaredField("myHandlers")
        handlersField.isAccessible = true
        return (handlersField.get(fileSystem!!) as Map<*, *>).size
    }

    private
    fun compileScript() {
        compileKotlinScriptToDirectory(
            root.resolve("classes").apply { mkdir() },
            KotlinCompilerOptions(),
            file("script.kts").apply { writeText("val x = 1") },
            scriptDefinitionFromTemplate(
                template = ThePinnedScriptTemplate::class,
                implicitImports = emptyList()
            ),
            testRuntimeClassPath.asFiles,
            mock()
        ) { it }
    }
}
