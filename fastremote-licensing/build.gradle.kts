// SPDX-License-Identifier: MPL-2.0
plugins { id("fastremote.java-conventions") }

val commercial = rootProject.extra["fastremoteEdition"] == "commercial"
if (commercial) {
    sourceSets.main { java.srcDir("src/commercial/java") }
    sourceSets.test { java.srcDir("src/commercialTest/java") }
}

dependencies {
    api(project(":fastremote-domain"))
    if (commercial) {
        implementation(libs.jackson.databind)
        implementation(libs.jackson.datatype.jdk8)
    }
    testImplementation(libs.junit.jupiter)
}
