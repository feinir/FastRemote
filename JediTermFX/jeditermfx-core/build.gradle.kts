import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("fastremote.java-conventions")
}

description = "FastRemote maintained JediTermFX core fork"
group = "com.techsenger.jeditermfx"
version = "1.1.0-fastremote.1"

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.addAll(listOf(
        "--patch-module",
        "com.techsenger.jeditermfx.core=${sourceSets.main.get().output.asPath}",
    ))
}

tasks.test {
    modularity.inferModulePath.set(false)
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.jetbrains.annotations)
    implementation(libs.slf4j.api)
}
