// SPDX-License-Identifier: MPL-2.0
plugins {
    id("fastremote.java-conventions")
    id("fastremote.javafx-conventions")
}

group = "org.fxmisc.richtext"
version = "${libs.versions.richtextfx.get()}-fastremote.1"
description = "FastRemote maintained RichTextFX source fork"

dependencies {
    api(libs.reactfx)
    api(libs.undofx)
    api(libs.flowless)
    api(libs.wellbehavedfx)
}
