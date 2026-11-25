// SPDX-License-Identifier: MPL-2.0
plugins {
    id("fastremote.application-packaging")
}

val commercial = rootProject.extra["fastremoteEdition"] == "commercial"
sourceSets.main { java.srcDir(if (commercial) "src/commercial/java" else "src/open/java") }
if (commercial) sourceSets.test { java.srcDir("src/commercialTest/java") }

dependencies {
    implementation(project(":fastremote-domain"))
    implementation(project(":fastremote-i18n"))
    implementation(project(":fastremote-transport"))
    implementation(project(":fastremote-terminal"))
    implementation(project(":fastremote-storage"))
    implementation(project(":fastremote-licensing"))
    implementation(project(":fastremote-ai"))
    implementation(project(":fastremote-mcp"))
    implementation(project(":fastremote-ui"))
    implementation(libs.jackson.databind)
    implementation(libs.pty4j)
    // Pty4J already puts this exact JNA artifact in the runtime image. Declaring it directly
    // only makes the existing bridge available to the macOS Dock integration at compile time.
    implementation(libs.jna)
    // Reuse JNA's typed Win32 handles and message structures for the native Windows tray.
    implementation(libs.jna.platform)
    implementation(libs.dbus.java.core)
    runtimeOnly(libs.dbus.java.native.unixsocket)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    runtimeOnly(libs.slf4j.nop)
    testImplementation(libs.mina.sshd.core)
    testImplementation(libs.mina.sshd.sftp)
}

// The existing Finder bridge subclasses Glass. With bundled system modules,
// javac --release rejects that existing export; use the pinned JDK 21 compiler.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(null as Int?)
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.compilerArgs.addAll(listOf("--add-exports", "javafx.graphics/com.sun.glass.ui=ALL-UNNAMED"))
}
