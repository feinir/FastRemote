// SPDX-License-Identifier: MPL-2.0
plugins {
    id("fastremote.java-conventions")
}

dependencies {
    implementation(project(":fastremote-domain"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.bouncycastle.provider)
    implementation(libs.snakeyaml.engine)
}
