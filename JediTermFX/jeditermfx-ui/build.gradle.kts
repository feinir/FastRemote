import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("fastremote.java-conventions")
    alias(libs.plugins.javafx)
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

val javafxVersion = extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("javafx")
    .get()
    .requiredVersion

javafx {
    version = javafxVersion
    modules("javafx.controls")
}

dependencies {
    api(project(":jeditermfx-core"))
    api(libs.kotlin.stdlib)
    api(libs.jetbrains.annotations)
    implementation(libs.slf4j.api)
}
