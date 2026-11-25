// SPDX-License-Identifier: MPL-2.0
plugins { id("fastremote.java-conventions") }

dependencies {
    api(project(":fastremote-domain"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jdk8)
    testImplementation(libs.junit.jupiter)
}
