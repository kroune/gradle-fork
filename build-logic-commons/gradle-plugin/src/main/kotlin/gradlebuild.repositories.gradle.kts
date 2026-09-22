/*
 * Copyright 2020 the original author or authors.
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

repositories {
    // Custom Kotlin compiler build (KT-86313 prototype). Override with the CUSTOM_KOTLIN_REPO_URL env var for local testing.
    maven {
        name = "Custom Kotlin repository"
        url = uri((providers.environmentVariable("CUSTOM_KOTLIN_REPO_URL").orElse(providers.gradleProperty("customKotlinRepoUrl")).orNull) ?: "https://raw.githubusercontent.com/kroune/maven-repo/main")
        content {
            includeVersionByRegex("org\\.jetbrains\\.kotlin", ".*", "2\\.4\\.10-fir-cache-.*")
        }
    }
    maven {
        name = "Gradle public repository"
        url = uri("https://repo.gradle.org/gradle/public")
        content {
            includeGroup("net.rubygrapefruit")
            includeGroup("org.gradle.fileevents")
            includeModule("flot", "flot")
            includeModule("org.gradle", "gradle-tooling-api")
            // The root multiplatform module plus the -jvm module its GMM redirects to.
            includeModuleByRegex("org\\.gradle\\.buildtool\\.internal", "configuration-cache-report(-jvm)?")
            includeModule("org.gradle.buildtool.internal", "gradle-ide-starter")
            includeModule("org.gradle.buildtool.internal", "gradle-ide-starter-scenarios")
        }
    }
    google {
        content {
            includeGroup("com.android.databinding")
            includeGroupByRegex("com\\.android\\.tools(\\.[a-z.\\-]*)?")
        }
    }
    maven {
        name = "CHAMP libs"
        url = uri("https://releases.usethesource.io/maven/")
        mavenContent {
            includeGroup("io.usethesource")
        }
    }
    mavenCentral()
}
