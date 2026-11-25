// SPDX-License-Identifier: MPL-2.0
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("fastremote.java-conventions")
    id("fastremote.javafx-conventions")
}


val tm4eGrammarSource = configurations.create("tm4eGrammarSource") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val tm4eGrammarDirectories = listOf(
    "bat", "cpp", "csharp", "css", "dart", "diff", "docker", "dotenv", "go", "groovy",
    "html", "ini", "java", "javascript", "json", "less", "lua", "make", "markdown", "perl",
    "php", "powershell", "python", "r", "ruby", "rust", "scss", "shellscript", "swift",
    "typescript", "xml", "yaml",
)

dependencies {
    implementation(project(":fastremote-domain"))
    implementation(project(":fastremote-i18n"))
    implementation(project(":fastremote-ai"))
    implementation(libs.jackson.databind)
    implementation(project(":richtextfx"))
    implementation(libs.flowless)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.tm4e.core)
    add(tm4eGrammarSource.name, libs.tm4e.language.pack)
    implementation(libs.gson)
    implementation(libs.jcodings)
    implementation(libs.joni)
    implementation(libs.dbus.java.core)
    runtimeOnly(libs.dbus.java.native.unixsocket)
    testRuntimeOnly(project(":fastremote-terminal"))
}

tasks.named<ProcessResources>("processResources") {
    val edition = rootProject.extra["fastremoteEdition"] as String
    val commercial = edition == "commercial"
    val shellArithmeticRule = file("src/main/textmate/shell-arithmetic-dollar.json")
    inputs.file(shellArithmeticRule)
    inputs.property("productVersion", project.version.toString())
    inputs.property("edition", edition)
    filesMatching("com/ynpe/fastremote/ui/legal/product.properties") {
        expand(
            "version" to project.version.toString(),
            "legalEntity" to providers.gradleProperty("legalEntity").get(),
            "copyrightYear" to providers.gradleProperty("copyrightYear").get(),
            "edition" to edition,
        )
    }
    from(rootProject.file(if (commercial) "LICENSE" else "LICENSES/MPL-2.0.txt")) {
        into("com/ynpe/fastremote/ui/legal")
        rename { "LICENSE.txt" }
    }
    from(rootProject.files(
        rootProject.file("THIRD_PARTY_NOTICES.txt"),
        rootProject.file("PRIVACY.md"),
        rootProject.file("TRADEMARKS.md"),
    )) {
        into("com/ynpe/fastremote/ui/legal")
    }
    from(rootProject.file(if (commercial) "SOURCE_OFFER.txt" else "LICENSES/COMMUNITY_SOURCE_OFFER.txt")) {
        into("com/ynpe/fastremote/ui/legal")
        rename { "SOURCE_OFFER.txt" }
    }
    if (commercial) from(rootProject.file("EULA.md")) {
        into("com/ynpe/fastremote/ui/legal")
    }
    from({ tm4eGrammarSource.map(::zipTree) }) {
        tm4eGrammarDirectories.forEach { directory ->
            include("syntaxes/$directory/*.tmLanguage.json")
            include("syntaxes/$directory/*.tmLanguage.plist")
        }
        // The pinned language pack references this missing repository rule.
        // Supply arithmetic expansion before subshell parsing can consume it.
        filesMatching("syntaxes/shellscript/shellscript.tmLanguage.json") {
            filter { line: String ->
                if (line.trim() == "\"arithmetic_double\": {")
                    "\"arithmetic_dollar\": " + shellArithmeticRule.readText().trim() + ",\n" + line
                else line
            }
        }
    }
    from({ tm4eGrammarSource.map(::zipTree) }) {
        include("about.html")
        into("META-INF/fastremote/textmate")
        rename { "LANGUAGE_PACK_ABOUT.html" }
    }
}
