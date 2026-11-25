// SPDX-License-Identifier: MPL-2.0
plugins {
    id("fastremote.java-conventions")
}

dependencies {
    implementation(project(":fastremote-domain"))
    implementation(project(":fastremote-i18n"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.jsr310)
}
