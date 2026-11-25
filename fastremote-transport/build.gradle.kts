// SPDX-License-Identifier: MPL-2.0
plugins {
    id("fastremote.java-conventions")
}

dependencies {
    implementation(project(":fastremote-domain"))
    implementation(libs.mina.sshd.core)
    implementation(libs.mina.sshd.sftp)
}
