// SPDX-License-Identifier: MPL-2.0
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.JavaExec

plugins { id("fastremote.java-conventions") }

val fxModules = "javafx.controls,javafx.fxml,javafx.swing"
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", fxModules))
}
tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules=$fxModules", "--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED")
}
tasks.withType<JavaExec>().configureEach {
    jvmArgs("--add-modules=$fxModules")
}

val libericaLauncher = javaToolchains.launcherFor(java.toolchain)
val verifyLibericaFull = tasks.register("verifyLibericaFull") {
    group = "verification"
    description = "Checks the pinned Liberica Full runtime before compiling or running JavaFX."
    inputs.property("runtimeVersion", libericaLauncher.map { it.metadata.javaRuntimeVersion })
    inputs.dir(libericaLauncher.map { it.metadata.installationPath.dir("jmods") })
    doLast {
        val metadata = libericaLauncher.get().metadata
        check(metadata.javaRuntimeVersion == "21.0.12.1+1-LTS") {
            "FastRemote 1.2.x requires Liberica Full 21.0.12.1+1-LTS; set JAVA_HOME to the target architecture JDK"
        }
        listOf("javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml", "javafx.swing").forEach { name ->
            check(metadata.installationPath.file("jmods/$name.jmod").asFile.isFile) {
                "Liberica Full is required; the selected JDK does not provide $name"
            }
        }
    }
}
tasks.withType<JavaCompile>().configureEach { dependsOn(verifyLibericaFull) }
tasks.withType<Test>().configureEach { dependsOn(verifyLibericaFull) }
tasks.withType<JavaExec>().configureEach { dependsOn(verifyLibericaFull) }
