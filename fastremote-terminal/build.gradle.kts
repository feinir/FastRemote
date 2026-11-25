// SPDX-License-Identifier: MPL-2.0
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("fastremote.java-conventions")
    id("fastremote.javafx-conventions")
}


dependencies {
    implementation(project(":fastremote-domain"))
    implementation(project(":fastremote-i18n"))
    implementation(project(":jeditermfx-core"))
    implementation(project(":jeditermfx-ui"))
}
