// SPDX-License-Identifier: MPL-2.0
plugins {
    base
}

group = "com.ynpe.fastremote"
version = providers.gradleProperty("appVersion").orElse("1.0.0").get()

val agentSourceAvailable = file("fastremote-license-agent/go.mod").isFile
val edition = providers.gradleProperty("fastremoteEdition")
    .orElse(if (agentSourceAvailable) "commercial" else "community").get()
require(edition in setOf("commercial", "community", "store")) {
    "fastremoteEdition must be commercial, community, or store"
}
require(edition != "commercial" || agentSourceAvailable) {
    "The commercial edition requires the private FastRemote License Agent sources"
}
extra["fastremoteEdition"] = edition

allprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
    dependsOn("verifyNoHardcodedUserFacingChinese")
}

tasks.register("verifyNoHardcodedUserFacingChinese") {
    group = "verification"
    description = "Rejects Chinese UI literals outside localization resources."
    val javaSources = files(
        "fastremote-ui/src/main/java",
        "fastremote-app/src/main/java",
        "fastremote-ai/src/main/java",
        "fastremote-terminal/src/main/java",
    ).asFileTree.matching { include("**/*.java") }
    val fxmlSources = fileTree("fastremote-ui/src/main/resources") { include("**/*.fxml") }
    inputs.files(javaSources, fxmlSources)
    doLast {
        val han = "[\\u3400-\\u9fff]"
        val javaLiteral = Regex("\\\"(?:\\\\.|[^\\\"\\\\\\n])*$han(?:\\\\.|[^\\\"\\\\\\n])*\\\"")
        val fxmlAttribute = Regex("(?:text|promptText|accessibleText)=\\\"[^\\\"]*$han[^\\\"]*\\\"")
        val violations = mutableListOf<String>()
        javaSources.files.sorted().forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                if (javaLiteral.containsMatchIn(line)) violations += "${source.relativeTo(rootDir)}:${index + 1}"
            }
        }
        fxmlSources.files.sorted().forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                if (fxmlAttribute.containsMatchIn(line)) violations += "${source.relativeTo(rootDir)}:${index + 1}"
            }
        }
        if (violations.isNotEmpty()) throw GradleException(
            "User-facing Chinese text must use fastremote-i18n resources:\n" + violations.joinToString("\n"))
    }
}
