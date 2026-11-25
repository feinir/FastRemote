// SPDX-License-Identifier: MPL-2.0
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("fastremote.java-conventions")
    id("fastremote.javafx-conventions")
}

description = "FastRemote maintained JediTermFX JavaFX UI fork"
group = "com.techsenger.jeditermfx"
version = "1.1.0-fastremote.1"

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.addAll(listOf(
        "--patch-module",
        "com.techsenger.jeditermfx.ui=${sourceSets.main.get().output.asPath}",
    ))
}

tasks.test {
    modularity.inferModulePath.set(false)
}


dependencies {
    api(project(":jeditermfx-core"))
    api(libs.kotlin.stdlib)
    api(libs.jetbrains.annotations)
    implementation(libs.slf4j.api)
}
