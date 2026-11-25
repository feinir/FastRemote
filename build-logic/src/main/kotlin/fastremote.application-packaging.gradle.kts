// SPDX-License-Identifier: MPL-2.0
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.GradleException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.process.CommandLineArgumentProvider
import java.util.Locale
import java.util.Base64
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipFile
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File
import java.util.HexFormat
import javax.imageio.ImageIO

plugins {
    id("fastremote.java-conventions")
    application
    id("fastremote.javafx-conventions")
}


val edition = rootProject.extra["fastremoteEdition"] as String
val commercial = edition == "commercial"
val store = edition == "store"
val applicationIdentifier = "com.ynpe.fastremote"
val applicationMainClass = "com.ynpe.fastremote.app.FastRemoteLauncher"

application {
    mainClass = applicationMainClass
}



val applicationName = "FastRemote"
val applicationDescription = "SSH and SFTP client"
val applicationVendor = providers.gradleProperty("legalEntity").get()
val macLocalNetworkUsageDescription =
    "FastRemote needs access to your local network to connect to SSH and SFTP hosts you configure."
val windowsUpgradeUuid = "5d7a345a-48a9-4f2c-8adb-d6c84553ee19"

val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
val currentPlatform = when {
    osName.contains("win") -> "windows"
    osName.contains("mac") -> "macos"
    osName.contains("linux") -> "linux"
    else -> throw GradleException("Native packaging is unsupported on operating system: $osName")
}
val currentArchitecture = when (System.getProperty("os.arch").lowercase(Locale.ROOT)) {
    "x86_64", "amd64" -> "amd64"
    "aarch64", "arm64" -> "arm64"
    else -> throw GradleException("Native packaging is unsupported on architecture: ${System.getProperty("os.arch")}")
}
// Explicit project configuration takes precedence over stale shell GOROOT/PATH values.
val configuredGoHome = providers.gradleProperty("goHome")
    .orElse(providers.gradleProperty("goHome.$currentPlatform.$currentArchitecture"))
val goExecutable = configuredGoHome.map { home ->
    File(home, if (currentPlatform == "windows") "bin/go.exe" else "bin/go").absolutePath
}.orElse("go")

fun Exec.configureGoToolchain() {
    inputs.property("goExecutable", goExecutable)
    configuredGoHome.orNull?.let { home -> environment("GOROOT", home) }
}

val macosArtifactArchitecture = if (currentArchitecture == "arm64") "aarch64" else currentArchitecture

val packagingIcons = mapOf(
    "macos" to layout.projectDirectory.file("src/main/resources/com/ynpe/fastremote/app/icons/logo.icns"),
    "windows" to layout.projectDirectory.file("src/main/resources/com/ynpe/fastremote/app/icons/logo.ico"),
    "linux" to layout.projectDirectory.file("src/main/resources/com/ynpe/fastremote/app/icons/logo.png"),
)
val platformPackagingIcon = packagingIcons.getValue(currentPlatform)
val platformJpackageResources = layout.projectDirectory.dir("src/main/jpackage/$currentPlatform")
val licenseAgentSource = rootProject.layout.projectDirectory.dir("fastremote-license-agent")
val developmentAgentKeysFile = licenseAgentSource.file("dev.properties")
val productAgentKeysFile = licenseAgentSource.file("product.properties")
val licenseAgentName = if (currentPlatform == "windows") "fastremote-license-agent.exe" else "fastremote-license-agent"
val releaseLicenseAgent = layout.buildDirectory.file("license-agent/release/$licenseAgentName")
val compiledReleaseLicenseAgent = layout.buildDirectory.file("license-agent/compiled/$licenseAgentName")
val developmentLicenseAgent = layout.buildDirectory.file("license-agent/development/$licenseAgentName")
val developmentDataDirectory = rootProject.layout.projectDirectory.dir(
    if (commercial) ".fastremote-dev" else ".fastremote-dev/$edition")
val licenseServerUrl = providers.gradleProperty("licenseServerUrl")
val defaultPrismOrder = when (currentPlatform) {
    "windows" -> "d3d,j2d,sw"
    "linux" -> "es2,j2d,sw"
    else -> ""
}
val prismOrder = providers.gradleProperty("prismOrder")
    .map { value ->
        val normalized = value.trim().lowercase(Locale.ROOT)
        val supported = when (currentPlatform) {
            "windows" -> setOf("d3d", "j2d", "sw")
            "linux" -> setOf("es2", "j2d", "sw")
            else -> emptySet()
        }
        val requested = normalized.split(',').map(String::trim)
        if (supported.isEmpty() || requested.isEmpty() || requested.any(String::isEmpty)
                || requested.toSet().size != requested.size || requested.any { it !in supported }) {
            throw GradleException(
                "-PprismOrder is invalid for $currentPlatform; supported pipelines are ${supported.joinToString(",")}"
            )
        }
        requested.joinToString(",")
    }
    .orElse(defaultPrismOrder)
val macSigningIdentity = providers.gradleProperty("macSigningIdentity")
    .orElse(providers.environmentVariable("FASTREMOTE_MAC_SIGNING_IDENTITY"))
    .map(String::trim)
val macNotaryProfile = providers.gradleProperty("macNotaryProfile")
    .orElse(providers.environmentVariable("FASTREMOTE_MAC_NOTARY_PROFILE"))
    .map(String::trim)
val macEntitlements = layout.projectDirectory.file("src/main/jpackage/macos/" +
    if (store) "fastremote-store.entitlements" else "fastremote.entitlements")
val signMacJarNativesScript = rootProject.layout.projectDirectory.file("scripts/sign-macos-jar-natives.sh")
val signedMacDmgScript = rootProject.layout.projectDirectory.file("scripts/package-signed-macos-dmg.sh")

tasks.named<ProcessResources>("processResources") {
    inputs.property("appVersion", project.version.toString())
    inputs.property("edition", edition)
    filesMatching("com/ynpe/fastremote/app/product.properties") {
        expand("version" to project.version.toString(), "edition" to edition)
    }
}

fun loadEmbeddedAgentKeys(product: Boolean): packaging.EmbeddedAgentKeys =
    packaging.loadEmbeddedAgentKeys(
        (if (product) productAgentKeysFile else developmentAgentKeysFile).asFile, product)

val packageVersion = provider {
    project.version.toString().also { value ->
        if (!value.matches(Regex("[1-9][0-9]*(\\.[0-9]+){0,2}"))) {
            throw GradleException(
                "jpackage version '$value' is invalid. Use one to three numeric components " +
                    "with a positive first component, " +
                    "for example -PappVersion=1.2.3."
            )
        }
    }
}

val prebuiltReleaseToolsDirectory = provider {
    licenseAgentSource.dir("prebuilt/${packageVersion.get()}/$currentPlatform-$currentArchitecture")
}
val prebuiltReleaseLicenseAgent = prebuiltReleaseToolsDirectory.map { it.file(licenseAgentName) }
val prebuiltReleaseInputStamp = prebuiltReleaseToolsDirectory.map { it.file("BUILD_INPUTS.sha256") }
val manifestSignerName = if (currentPlatform == "windows") "fastremote-manifest.exe" else "fastremote-manifest"
val prebuiltManifestSigner = prebuiltReleaseToolsDirectory.map { it.file(manifestSignerName) }

fun releaseToolsInputFingerprint(version: String): String {
    val repositoryRoot = rootProject.layout.projectDirectory.asFile
    val inputs = licenseAgentSource.asFileTree.matching {
        include("**/*.go", "go.mod", "go.sum", "product.properties")
        exclude("prebuilt/**")
    }.files + rootProject.layout.projectDirectory.file("scripts/build-release-tools.sh").asFile
    val lines = inputs.map { file ->
        val relative = file.relativeTo(repositoryRoot).invariantSeparatorsPath
        val digest = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()))
        )
        "$relative=$digest"
    }.sorted()
    val canonical = buildString {
        append("version=").append(version).append('\n')
        lines.forEach { append(it).append('\n') }
    }
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    )
}

val javaToolchains = extensions.getByType<JavaToolchainService>()
val packagingJavaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
    vendor = JvmVendorSpec.BELLSOFT
}
val jpackageExecutable = packagingJavaLauncher.map { launcher ->
    val executableName = if (currentPlatform == "windows") "jpackage.exe" else "jpackage"
    launcher.metadata.installationPath.file("bin/$executableName").asFile
}

val mainJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
// Each Gradle invocation gets its own immutable snapshot. A concurrently running app may lazily
// load classes long after startup, so later check/build invocations must never replace its JARs.
val developmentRunDirectory = layout.buildDirectory.dir(
    "development-run/classpath/${UUID.randomUUID()}"
)
val prepareDevelopmentRun = tasks.register<Sync>("prepareDevelopmentRun") {
    group = "application"
    description = "Creates an immutable JAR snapshot for a long-running development application."
    dependsOn(tasks.named("jar"))
    from(mainJar)
    from(configurations.runtimeClasspath)
    into(developmentRunDirectory)
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

val buildDevelopmentLicenseAgent = tasks.register<Exec>("buildDevelopmentLicenseAgent") {
    group = "build"
    description = "Builds the local Go license agent used by Gradle run."
    workingDir(licenseAgentSource)
    configureGoToolchain()
    inputs.dir(licenseAgentSource)
    inputs.file(developmentAgentKeysFile)
    inputs.property("appVersion", project.version.toString())
    outputs.file(developmentLicenseAgent)
    doFirst {
        developmentLicenseAgent.get().asFile.parentFile.mkdirs()
        loadEmbeddedAgentKeys(false)
        commandLine(goExecutable.get(), "build", "-trimpath", "-ldflags",
            "-X main.buildVersion=${project.version} -X main.buildMode=development",
            "-o", developmentLicenseAgent.get().asFile.absolutePath, ".")
    }
}

tasks.named<JavaExec>("run") {
    dependsOn(prepareDevelopmentRun)
    classpath = files(fileTree(developmentRunDirectory) { include("*.jar") })
    if (commercial) {
        dependsOn(buildDevelopmentLicenseAgent)
        inputs.file(developmentAgentKeysFile)
        systemProperty("fastremote.license.agent", developmentLicenseAgent.get().asFile.absolutePath)
        systemProperty("fastremote.license.serverUrl", licenseServerUrl.orElse("http://127.0.0.1:8090").get())
    }
    if (prismOrder.get().isNotEmpty()) {
        systemProperty("prism.order", prismOrder.get())
    }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--user-data-dir", developmentDataDirectory.asFile.absolutePath)
    })
    jvmArgs("--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED")
    if (currentPlatform == "macos") {
        jvmArgs(
            // The Finder open-file bridge wraps JavaFX's existing Glass event handler. Gradle's
            // JavaFX convention launches development runs with javafx.graphics as a named module.
            "-Xdock:name=$applicationName",
        )
    }
}

val verifyDevelopmentRunClasspath = tasks.register("verifyDevelopmentRunClasspath") {
    group = "verification"
    description = "Verifies that development runs use a complete, isolated JAR classpath."
    dependsOn(prepareDevelopmentRun)
    inputs.dir(developmentRunDirectory)
    doLast {
        val snapshotDirectory = developmentRunDirectory.get().asFile.canonicalFile
        val runEntries = tasks.named<JavaExec>("run").get().classpath.files
        check(runEntries.isNotEmpty() && runEntries.all { entry ->
            entry.canonicalFile.parentFile == snapshotDirectory
        }) {
            "Development run classpath must only contain the isolated JAR snapshot"
        }
        check(tasks.named<JavaExec>("run").get().jvmArgs.orEmpty().contains(
            "--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED"
        )) {
            "Development runs must export Glass because the launcher loads the macOS bridge class on every platform"
        }
        val runArguments = tasks.named<JavaExec>("run").get().argumentProviders
            .flatMap { it.asArguments() }
        check(runArguments == listOf("--user-data-dir", developmentDataDirectory.asFile.absolutePath)) {
            "Development runs must use the repository-local isolated data directory"
        }
        val uiJar = runEntries.singleOrNull { it.name.startsWith("fastremote-ui-") }
            ?: throw GradleException("Development run snapshot does not contain fastremote-ui")
        ZipFile(uiJar).use { archive ->
            check(archive.getEntry("com/ynpe/fastremote/ui/HostEditorDialog.class") != null) {
                "Development run snapshot is missing HostEditorDialog"
            }
            check(archive.getEntry("com/ynpe/fastremote/ui/WorkspaceViewModel\$1.class") != null) {
                "Development run snapshot is missing the enum-switch class used to create workspace tabs"
            }
            check(archive.getEntry("com/ynpe/fastremote/ui/AiChatView.class") != null) {
                "Development run snapshot is missing the native AI chat adapter"
            }
            check(archive.getEntry("com/ynpe/fastremote/ui/chat/StreamDocumentView.class") != null) {
                "Development run snapshot is missing the native AI chat document"
            }
            check(archive.entries().asSequence().none { entry -> entry.name.endsWith(".js")
                || entry.name.contains("AiChatWebView") || entry.name == "com/ynpe/fastremote/ui/chat/index.html" }) {
                "Development run snapshot contains obsolete browser chat resources"
            }
        }
        val i18nJar = runEntries.singleOrNull { it.name.startsWith("fastremote-i18n-") }
            ?: throw GradleException("Development run snapshot does not contain fastremote-i18n")
        ZipFile(i18nJar).use { archive ->
            listOf("messages.properties", "messages_zh_CN.properties", "messages_zh_TW.properties",
                "messages_ja.properties", "messages_ko.properties", "messages_es.properties",
                "messages_pt_BR.properties", "messages_th.properties", "messages_fr.properties",
                "messages_it.properties", "messages_de.properties", "messages_ru.properties")
                .forEach { name ->
                    check(archive.getEntry("com/ynpe/fastremote/i18n/$name") != null) {
                        "Development run snapshot is missing published localization resource $name"
                    }
                }
        }
        val appJar = runEntries.singleOrNull { it.name.startsWith("fastremote-app-") }
            ?: throw GradleException("Development run snapshot does not contain fastremote-app")
        ZipFile(appJar).use { archive ->
            listOf("en", "zh_CN", "zh_TW", "ja", "ko", "es", "pt_BR", "th", "fr", "it", "de", "ru").forEach { locale ->
                check(archive.getEntry("com/ynpe/fastremote/app/defaults/commands_${locale}.tsv") != null) {
                    "Development run snapshot is missing the published $locale command catalog"
                }
            }
        }
        val terminalJar = runEntries.singleOrNull { it.name.startsWith("fastremote-terminal-") }
            ?: throw GradleException("Development run snapshot does not contain fastremote-terminal")
        ZipFile(terminalJar).use { archive ->
            check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/HarmonyOS_Sans_SC.ttf") != null) {
                "Development run snapshot is missing the bundled HarmonyOS Sans font"
            }
            check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/HarmonyOS_Sans_TC.ttf") != null) {
                "Development run snapshot is missing the bundled HarmonyOS Sans TC font"
            }
            listOf("NotoSansCJKjp-Regular.otf", "NotoSansCJKkr-Regular.otf", "NotoSansThai-Regular.ttf")
                .forEach { name ->
                    check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/$name") != null) {
                        "Development run snapshot is missing the bundled font $name"
                    }
                }
            check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/NOTO_OFL.txt") != null) {
                "Development run snapshot is missing the Noto OFL license"
            }
            check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/LICENSE-update.txt") != null) {
                "Development run snapshot is missing the HarmonyOS Sans license"
            }
            check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/SOURCE.txt") != null) {
                "Development run snapshot is missing the HarmonyOS Sans source notice"
            }
            check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/JetBrainsMonoNL-Regular.ttf") != null) {
                "Development run snapshot is missing the bundled JetBrains Mono NL font"
            }
            check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/JETBRAINS_MONO_OFL.txt") != null) {
                "Development run snapshot is missing the JetBrains Mono OFL license"
            }
            check(archive.getEntry("com/ynpe/fastremote/terminal/fonts/JETBRAINS_MONO_SOURCE.txt") != null) {
                "Development run snapshot is missing the JetBrains Mono source notice"
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyDevelopmentRunClasspath)
}

val verifyPackagingIcons = tasks.register("verifyPackagingIcons") {
    group = "verification"
    description = "Verifies that every native packaging icon exists and has the expected file signature."
    inputs.files(packagingIcons.values)

    doLast {
        val expectedSignatures = mapOf(
            "macos" to byteArrayOf(0x69, 0x63, 0x6e, 0x73),
            "windows" to byteArrayOf(0x00, 0x00, 0x01, 0x00),
            "linux" to byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47),
        )
        packagingIcons.forEach { (platform, icon) ->
            val iconFile = icon.asFile
            val expected = expectedSignatures.getValue(platform)
            val actual = iconFile.inputStream().use { it.readNBytes(expected.size) }
            if (!actual.contentEquals(expected)) {
                throw GradleException(
                    "Packaging icon for $platform has an invalid file signature: ${iconFile.absolutePath}"
                )
            }
        }
        val master = packagingIcons.getValue("linux").asFile
        val image = ImageIO.read(master)
            ?: throw GradleException("Packaging icon master cannot be decoded: ${master.absolutePath}")
        check(image.width == 1024 && image.height == 1024) {
            "Packaging icon master must be 1024x1024: ${master.absolutePath}"
        }
        val transparentMargin = 48
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (x < transparentMargin || x >= image.width - transparentMargin
                    || y < transparentMargin || y >= image.height - transparentMargin) {
                    check((image.getRGB(x, y) ushr 24) == 0) {
                        "Packaging icon artwork must keep at least ${transparentMargin}px of transparent padding"
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyPackagingIcons)
}

val packagingSubdirectory = if (commercial) "jpackage" else "jpackage/$edition"
val jpackageInputDirectory = layout.buildDirectory.dir("$packagingSubdirectory/input")
val legalBundleDirectory = layout.buildDirectory.dir("generated/legal/$edition")
val packagingOutputRoot = rootProject.layout.buildDirectory.dir(packagingSubdirectory)
val appImageDestination = packagingOutputRoot.map { it.dir("$currentPlatform/app-image") }
val appImage = appImageDestination.map { destination ->
    destination.dir(if (currentPlatform == "macos") "$applicationName.app" else applicationName)
}
val appContentDirectory = appImage.map { image ->
    when (currentPlatform) {
        "macos" -> image.dir("Contents/app")
        "windows" -> image.dir("app")
        "linux" -> image.dir("lib/app")
        else -> throw GradleException("Unsupported application image layout: $currentPlatform")
    }
}
val appImagePlatformOptions = when (currentPlatform) {
    "macos" -> listOf(
        "--mac-package-identifier", applicationIdentifier,
        "--mac-package-name", applicationName,
        "--mac-app-category", "developer-tools",
    )
    else -> emptyList()
}

val terminalFontDirectory = project(":fastremote-terminal")
    .layout.projectDirectory.dir("src/main/resources/com/ynpe/fastremote/terminal/fonts")
val uiIconDirectory = project(":fastremote-ui")
    .layout.projectDirectory.dir("src/main/resources/com/ynpe/fastremote/ui/icons")

val prepareLegalBundle = tasks.register<Sync>("prepareLegalBundle") {
    group = "distribution"
    description = "Generates the legal bundle for the selected edition."
    doFirst { delete(legalBundleDirectory) }

    from(rootProject.file(if (commercial) "LICENSE" else "LICENSES/MPL-2.0.txt")) {
        rename { "LICENSE" }
    }
    from(rootProject.file(if (commercial) "SOURCE_OFFER.txt" else "LICENSES/COMMUNITY_SOURCE_OFFER.txt")) {
        rename { "SOURCE_OFFER.txt" }
    }
    from(rootProject.files(
        rootProject.file("THIRD_PARTY_NOTICES.txt"),
        rootProject.file("PRIVACY.md"),
        rootProject.file("TRADEMARKS.md"),
    ))
    if (commercial) from(rootProject.file("EULA.md"))
    from(rootProject.layout.projectDirectory.dir("LICENSES")) {
        into("LICENSES")
    }
    from(rootProject.layout.projectDirectory.file("JediTermFX/LICENSE-APACHE-2.0.txt")) {
        into("LICENSES")
        rename { "Apache-2.0.txt" }
    }
    from(terminalFontDirectory) {
        include("*.txt")
        into("LICENSES/fonts")
    }
    from(uiIconDirectory) {
        include("*/NOTICE.md")
        into("LICENSES/icons")
    }
    from(packagingJavaLauncher.map { it.metadata.installationPath.dir("legal") }) {
        into("LICENSES/openjdk-runtime")
    }
    into(legalBundleDirectory)
    filePermissions { unix("644") }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

val verifyCommercialLegalBundle = tasks.register("verifyCommercialLegalBundle") {
    group = "verification"
    description = "Verifies the release legal bundle and concise product description."
    dependsOn(prepareLegalBundle)
    inputs.dir(legalBundleDirectory)
    inputs.property("applicationDescription", applicationDescription)
    inputs.files(
        rootProject.file("README.md"),
        rootProject.file("AGENTS.md"),
        rootProject.file("docs/architecture/ENGINEERING_DECISIONS.md"),
    )

    doLast {
        val bundle = legalBundleDirectory.get().asFile
        listOf(
            "LICENSE",
            "THIRD_PARTY_NOTICES.txt",
            "SOURCE_OFFER.txt",
            "PRIVACY.md",
            "TRADEMARKS.md",
            "LICENSES/EPL-1.0.txt",
            "LICENSES/EPL-2.0.txt",
            "LICENSES/commonmark-java-BSD-2-Clause.txt",
            "LICENSES/Apache-2.0.txt",
            "LICENSES/cloudflare-circl-BSD-3-Clause.txt",
            "LICENSES/fonts/LICENSE-update.txt",
            "LICENSES/fonts/NOTO_OFL.txt",
            "LICENSES/fonts/JETBRAINS_MONO_OFL.txt",
            "LICENSES/icons/lucide/NOTICE.md",
            "LICENSES/icons/intellij/NOTICE.md",
        ).forEach { relativePath ->
            check(bundle.resolve(relativePath).isFile) {
                "Generated legal bundle is missing $relativePath"
            }
        }
        val runtimeLicense = "LICENSES/openjdk-runtime/java.base/LICENSE"
        check(bundle.resolve("EULA.md").isFile == commercial) {
            "The commercial EULA must be present only in commercial packages"
        }
        check(bundle.resolve(runtimeLicense).isFile) {
            "Generated legal bundle is missing $runtimeLicense"
        }
        check(applicationDescription == "SSH and SFTP client") {
            "Native package description must be SSH and SFTP client; licensing belongs in About and legal notices"
        }
    }
}

val compileReleaseLicenseAgent = tasks.register<Exec>("compileReleaseLicenseAgent") {
    group = "distribution"
    description = "Builds the current-platform release Go license agent when no prebuilt binary is available."
    workingDir(licenseAgentSource)
    configureGoToolchain()
    inputs.dir(licenseAgentSource)
    inputs.property("applicationVersion", packageVersion)
    inputs.file(productAgentKeysFile)
    outputs.file(compiledReleaseLicenseAgent)
    onlyIf { !prebuiltReleaseLicenseAgent.get().asFile.isFile }
    doFirst {
        loadEmbeddedAgentKeys(true)
        compiledReleaseLicenseAgent.get().asFile.parentFile.mkdirs()
        val linkFlags = "-s -w -X main.buildVersion=${packageVersion.get()} -X main.buildMode=release"
        commandLine(goExecutable.get(), "build", "-tags", "product", "-trimpath", "-ldflags", linkFlags,
            "-o", compiledReleaseLicenseAgent.get().asFile.absolutePath, ".")
    }
}

val buildReleaseLicenseAgent = tasks.register<Sync>("buildReleaseLicenseAgent") {
    group = "distribution"
    description = "Stages the matching prebuilt release Agent, or compiles it locally as a fallback."
    dependsOn(compileReleaseLicenseAgent)
    inputs.property("applicationVersion", packageVersion)
    inputs.file(productAgentKeysFile)
    inputs.files(prebuiltReleaseLicenseAgent)
    from(provider {
        val prebuilt = prebuiltReleaseLicenseAgent.get().asFile
        if (prebuilt.isFile) prebuilt else compiledReleaseLicenseAgent.get().asFile
    })
    into(releaseLicenseAgent.map { it.asFile.parentFile })
    rename { licenseAgentName }
    filePermissions { unix("755") }
    doFirst {
        loadEmbeddedAgentKeys(true)
        val prebuilt = prebuiltReleaseLicenseAgent.get().asFile
        if (prebuilt.isFile && prebuilt.length() == 0L) {
            throw GradleException("Prebuilt release Agent is empty: ${prebuilt.absolutePath}")
        }
        if (prebuilt.isFile) {
            val stamp = prebuiltReleaseInputStamp.get().asFile
            val expected = releaseToolsInputFingerprint(packageVersion.get())
            if (!stamp.isFile || stamp.readText().trim() != expected) {
                throw GradleException(
                    "Prebuilt release tools do not match the current Agent inputs. " +
                        "Run ./scripts/build-release-tools.sh ${packageVersion.get()} on the trusted build host."
                )
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyCommercialLegalBundle)
}

val prepareJpackageInput = tasks.register<Sync>("prepareJpackageInput") {
    group = "distribution"
    description = "Collects the application, dependencies, and legal bundle for jpackage."
    dependsOn(tasks.named("jar"), prepareLegalBundle)
    doFirst { delete(jpackageInputDirectory) }

    from(mainJar)
    from(configurations.runtimeClasspath)
    from(legalBundleDirectory) {
        into("legal")
    }
    if (commercial) {
        dependsOn(buildReleaseLicenseAgent)
        from(releaseLicenseAgent) {
            filePermissions { unix("755") }
        }
    }
    into(jpackageInputDirectory)
    filePermissions { unix("644") }
    duplicatesStrategy = DuplicatesStrategy.FAIL

    if (!commercial) doLast {
        val input = jpackageInputDirectory.get().asFile
        check(!input.resolve(licenseAgentName).exists() && !input.resolve("fastremote-integrity.json").exists()) {
            "An open-source package must exclude commercial authorization artifacts"
        }
        val licensingJar = input.listFiles().orEmpty().single { it.name.startsWith("fastremote-licensing-") }
        ZipFile(licensingJar).use { archive ->
            check(archive.entries().asSequence().none {
                it.name.contains("GoAgentLicenseGateway") || it.name.contains("ReleaseIntegrityVerifier")
            }) { "An open-source package must exclude the commercial authorization verifier" }
        }
    }

    if (currentPlatform == "macos") {
        inputs.file(signMacJarNativesScript)
        inputs.property("macSigningIdentity", macSigningIdentity.orElse(""))
        doLast {
            val signingIdentity = macSigningIdentity.orNull.orEmpty()
            if (signingIdentity.isNotEmpty()) {
                val process = ProcessBuilder(
                    signMacJarNativesScript.asFile.absolutePath,
                    jpackageInputDirectory.get().asFile.absolutePath,
                    signingIdentity,
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.waitFor() != 0) {
                    throw GradleException("Signing embedded macOS JAR binaries failed: $output")
                }
                output.lineSequence().filter(String::isNotBlank).forEach(logger::lifecycle)
            }
        }
    }
}

val cleanPackageAppImage = tasks.register<Delete>("cleanPackageAppImage") {
    // Finder may create .DS_Store in the destination while it is displaying a previously built
    // App Image. Delete only the product directory so that concurrent Finder metadata cannot make
    // Gradle's recursive deletion of the parent fail.
    delete(appImage.map { directory -> directory.asFile })
}

val verifyPackagingInputs = tasks.register("verifyPackagingInputs") {
    group = "verification"
    description = "Checks packaging assets without rerunning the development test suite."
    dependsOn(verifyPackagingIcons, verifyCommercialLegalBundle)
}

val macStoreMinimumSystemVersion = "12.0"
val macStoreBuildVersion = providers.gradleProperty("macStoreBuildVersion").orElse(packageVersion).map { version ->
    check(version.matches(Regex("[1-9][0-9]{0,3}(\\.[0-9]{1,2}){0,2}"))) {
        "Invalid macStoreBuildVersion '$version'. Use an Apple build number such as 2.1.6."
    }
    version
}
val macStoreJpackageResources = layout.buildDirectory.dir("jpackage/store/macos/resources")
val prepareMacStoreJpackageResources = tasks.register<Sync>("prepareMacStoreJpackageResources") {
    from(platformJpackageResources)
    into(macStoreJpackageResources)
    inputs.property("minimumSystemVersion", macStoreMinimumSystemVersion)
    inputs.property("buildVersion", macStoreBuildVersion)
    filesMatching("Info.plist") {
        filter { line: String ->
            line.replace("<string>10.11</string>", "<string>$macStoreMinimumSystemVersion</string>")
                .replace("DEPLOY_BUNDLE_CFBUNDLE_VERSION", macStoreBuildVersion.get())
        }
    }
}
val appImageJpackageResources = if (store && currentPlatform == "macos") {
    macStoreJpackageResources
} else {
    provider { platformJpackageResources }
}

val createAppImage = tasks.register<Exec>("createAppImage") {
    group = "distribution"
    description = "Runs jpackage so platform signing finalizes the embedded license agent before hashing it."
    dependsOn(verifyPackagingInputs, prepareJpackageInput, cleanPackageAppImage)

    inputs.dir(jpackageInputDirectory)
    inputs.file(platformPackagingIcon)
    if (store && currentPlatform == "macos") dependsOn(prepareMacStoreJpackageResources)
    if ((store && currentPlatform == "macos") || platformJpackageResources.asFile.isDirectory) {
        inputs.dir(appImageJpackageResources)
    }
    inputs.property("applicationVersion", packageVersion)
    inputs.property("applicationDescription", applicationDescription)
    inputs.property("prismOrder", prismOrder)
    inputs.property("macSigningIdentity", macSigningIdentity.orElse(""))
    if (commercial) inputs.file(productAgentKeysFile)
    inputs.property("edition", edition)
    if (currentPlatform == "macos") inputs.file(macEntitlements)
    // Declare the parent as the task output: Gradle may create an output directory before Exec,
    // while jpackage requires the FastRemote.app target itself not to exist yet.
    outputs.dir(appImageDestination)

    doFirst {
        val productKeys = if (commercial) loadEmbeddedAgentKeys(true) else null
        check(!store || currentPlatform == "macos") { "The store app image must be built on macOS" }
        val destination = appImageDestination.get().asFile
        destination.mkdirs()

        commandLine(
            buildList {
                add(jpackageExecutable.get().absolutePath)
                addAll(
                    listOf(
                        "--type", "app-image",
                        // fr uses the bundled console Java launcher, including on Windows.
                        // jpackage otherwise defaults to --strip-native-commands.
                        "--jlink-options", "--strip-debug --no-man-pages --no-header-files" +
                            if (store) " --strip-native-commands" else "",
                        // D-Bus SASL uses UnixSystem reflectively; java.se does not include its module.
                        "--add-modules", "java.se,javafx.controls,javafx.fxml,javafx.swing,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.zipfs,jdk.management,jdk.httpserver,jdk.security.auth",
                        "--java-options", "--add-modules=javafx.controls,javafx.fxml,javafx.swing",
                        "--java-options", "--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
                        "--dest", destination.absolutePath,
                        "--input", jpackageInputDirectory.get().asFile.absolutePath,
                        "--name", applicationName,
                        "--main-jar", mainJar.get().asFile.name,
                        "--main-class", applicationMainClass,
                        "--app-version", packageVersion.get(),
                        "--description", applicationDescription,
                        "--vendor", applicationVendor,
                        "--icon", platformPackagingIcon.asFile.absolutePath,
                        "--java-options", "-Dfile.encoding=UTF-8",
                    )
                )
                if (productKeys != null) {
                    addAll(listOf("--java-options",
                        "-Dfastremote.license.manifestPublicKeys=${productKeys.manifestKeyId}=${productKeys.manifestPublicKey}"))
                }
                if (prismOrder.get().isNotEmpty()) {
                    addAll(listOf("--java-options", "-Dprism.order=${prismOrder.get()}"))
                }
                addAll(appImagePlatformOptions)
                val signingIdentity = macSigningIdentity.orNull.orEmpty()
                if (currentPlatform == "macos" && signingIdentity.isNotEmpty()) {
                    val prefix = if (store) "3rd Party Mac Developer Application:" else "Developer ID Application:"
                    if (!signingIdentity.startsWith(prefix)) {
                        throw GradleException(
                            "macSigningIdentity must begin with $prefix for the $edition edition"
                        )
                    }
                    if (store) add("--mac-app-store")
                    addAll(
                        listOf(
                            "--mac-sign",
                            "--mac-signing-key-user-name", if (store) signingIdentity.substringAfter(": ") else signingIdentity,
                            "--mac-entitlements", macEntitlements.asFile.absolutePath,
                        )
                    )
                }
                val resources = appImageJpackageResources.get().asFile
                if (resources.isDirectory) {
                    addAll(listOf("--resource-dir", resources.absolutePath))
                }
            }
        )
    }
    doLast {
        val runtimeRelease = appImage.get().asFile.resolve(
            when (currentPlatform) {
                "macos" -> "Contents/runtime/Contents/Home/release"
                "windows" -> "runtime/release"
                else -> "lib/runtime/release"
            }
        )
        val runtimeProperties = Properties().apply {
            runtimeRelease.inputStream().use { load(it) }
        }
        val consoleJava = runtimeRelease.parentFile.resolve(
            if (currentPlatform == "windows") "bin/java.exe" else "bin/java"
        )
        check(store || (consoleJava.isFile && (currentPlatform == "windows" || consoleJava.canExecute()))) {
            "Packaged runtime is missing the console Java launcher required by fr"
        }
        val runtimeModules = runtimeProperties.getProperty("MODULES", "")
            .trim('"').split(' ').toSet()
        check("jdk.security.auth" in runtimeModules) {
            "Packaged runtime is missing jdk.security.auth required by D-Bus SASL/UnixSystem"
        }
        val legalRoots = appImage.get().asFile.walkTopDown()
            .filter { file -> file.isFile && file.name == "THIRD_PARTY_NOTICES.txt" }
            .map { file -> file.parentFile }
            .toList()
        check(legalRoots.size == 1) {
            "Packaged App Image must contain exactly one legal bundle, found ${legalRoots.size}"
        }
        val legalRoot = legalRoots.single()
        listOf("LICENSE", "SOURCE_OFFER.txt", "PRIVACY.md", "TRADEMARKS.md")
            .forEach { name ->
                check(legalRoot.resolve(name).isFile) {
                    "Packaged App Image legal bundle is missing $name"
                }
            }
        check(legalRoot.resolve("LICENSES/EPL-1.0.txt").isFile) {
            "Packaged App Image legal bundle is missing the EPL 1.0 license"
        }
        check(legalRoot.resolve("LICENSES/EPL-2.0.txt").isFile) {
            "Packaged App Image legal bundle is missing the EPL 2.0 license"
        }
    }
}

val integrityManifest = appContentDirectory.map { it.file("fastremote-integrity.json") }
val windowsCertificate = providers.environmentVariable("FASTREMOTE_WINDOWS_CERTIFICATE_THUMBPRINT")
val windowsSignTool = providers.environmentVariable("FASTREMOTE_WINDOWS_SIGNTOOL")
fun runWindowsAuthenticode(target: File, verifyOnly: Boolean = false) {
    val tool = windowsSignTool.orNull ?: throw GradleException("FASTREMOTE_WINDOWS_SIGNTOOL is required")
    val command = mutableListOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        rootProject.file("scripts/sign-windows-release.ps1").absolutePath,
        "-Path", target.absolutePath, "-SignTool", tool)
    if (verifyOnly) command.add("-VerifyOnly")
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    process.inputStream.bufferedReader().useLines { lines -> lines.forEach { logger.lifecycle(it) } }
    check(process.waitFor() == 0) { "Windows Authenticode failed for $target" }
}
val signWindowsAppImage = tasks.register("signWindowsAppImage") {
    group = "distribution"
    description = "Signs packaged Windows PE files, including JAR natives, before integrity hashing."
    dependsOn(createAppImage)
    onlyIf { currentPlatform == "windows" }
    doLast { runWindowsAuthenticode(appImage.get().asFile) }
}
val generateIntegrityManifest = tasks.register<Exec>("generateIntegrityManifest") {
    group = "distribution"
    description = "Hashes the final packaged license agent and application JARs, then signs their integrity manifest."
    dependsOn(signWindowsAppImage)
    workingDir(licenseAgentSource)
    configureGoToolchain()
    inputs.files(appContentDirectory.map { directory -> directory.asFileTree.matching {
        include("*.jar", licenseAgentName)
    } })
    inputs.file(productAgentKeysFile)
    inputs.files(prebuiltManifestSigner)
    outputs.file(integrityManifest)
    outputs.upToDateWhen { false }
    doFirst {
        if (System.getenv("FASTREMOTE_MANIFEST_PRIVATE_KEY_BASE64").isNullOrBlank()) {
            throw GradleException("FASTREMOTE_MANIFEST_PRIVATE_KEY_BASE64 is required for release packaging")
        }
        val keyId = loadEmbeddedAgentKeys(true).manifestKeyId
        val signer = prebuiltManifestSigner.get().asFile
        commandLine(
            *(if (signer.isFile) arrayOf(signer.absolutePath) else arrayOf("go", "run", "./cmd/manifest")),
            "--directory", appContentDirectory.get().asFile.absolutePath,
            "--agent", licenseAgentName,
            "--key-id", keyId,
            "--output", integrityManifest.get().asFile.absolutePath,
        )
    }
}

val sealPackagedAppImage = if (currentPlatform == "macos") {
    var agentHashBeforeSeal = byteArrayOf()
    tasks.register<Exec>("sealPackagedAppImage") {
        group = "distribution"
        description = "Re-seals the outer macOS app after adding the signed integrity manifest without changing the agent."
        dependsOn(generateIntegrityManifest)
        inputs.file(integrityManifest)
        outputs.upToDateWhen { false }
        doFirst {
            agentHashBeforeSeal = MessageDigest.getInstance("SHA-256")
                .digest(appContentDirectory.get().file(licenseAgentName).asFile.readBytes())
            val signingIdentity = macSigningIdentity.orNull.orEmpty()
            commandLine(
                buildList {
                    addAll(listOf("codesign", "--force"))
                    if (signingIdentity.isEmpty()) {
                        addAll(listOf("--sign", "-"))
                    } else {
                        addAll(
                            listOf(
                                "--sign", signingIdentity,
                                "--options", "runtime",
                                "--timestamp",
                                "--entitlements", macEntitlements.asFile.absolutePath,
                            )
                        )
                    }
                    add(appImage.get().asFile.absolutePath)
                }
            )
        }
        doLast {
            val agentHashAfterSeal = MessageDigest.getInstance("SHA-256")
                .digest(appContentDirectory.get().file(licenseAgentName).asFile.readBytes())
            check(MessageDigest.isEqual(agentHashBeforeSeal, agentHashAfterSeal)) {
                "Outer macOS app sealing unexpectedly changed the hashed license agent"
            }
            if (macSigningIdentity.orNull.orEmpty().isNotEmpty()) {
                val verify = ProcessBuilder(
                    "codesign", "--verify", "--deep", "--strict", "--verbose=2",
                    appImage.get().asFile.absolutePath,
                ).redirectErrorStream(true).start()
                val verifyOutput = verify.inputStream.bufferedReader().use { it.readText() }
                if (verify.waitFor() != 0) {
                    throw GradleException("Developer ID App verification failed: $verifyOutput")
                }
            }
        }
    }
} else {
    tasks.register("sealPackagedAppImage") {
        group = "distribution"
        description = "Finalizes the application image after adding the signed integrity manifest."
        dependsOn(generateIntegrityManifest)
    }
}

val packageAppImage = tasks.register("packageAppImage") {
    group = "distribution"
    description = "Creates a self-contained FastRemote application image for the current platform."
    when {
        commercial -> dependsOn(sealPackagedAppImage)
        currentPlatform == "windows" && windowsCertificate.isPresent -> dependsOn(signWindowsAppImage)
        else -> dependsOn(createAppImage)
    }

    doLast {
        if (!commercial) {
            check(!appContentDirectory.get().file(licenseAgentName).asFile.exists()) {
                "A $edition package cannot contain the commercial License Agent"
            }
        }
        if (currentPlatform == "macos" && macSigningIdentity.orNull.orEmpty().isNotEmpty()) {
            val verify = ProcessBuilder("codesign", "--verify", "--deep", "--strict",
                appImage.get().asFile.absolutePath).inheritIO().start()
            check(verify.waitFor() == 0) { "Application signature verification failed" }
        }
        if (currentPlatform == "macos") {
            val infoPlist = appImage.get().file("Contents/Info.plist").asFile
            val launcherConfig = appImage.get()
                .file("Contents/app/$applicationName.cfg").asFile
                .readText()
            check("-Xdock:icon" !in launcherConfig && "applicationIconImage" !in launcherConfig) {
                "Packaged macOS launcher must not set a runtime Dock icon"
            }
            val readIdentifier = ProcessBuilder(
                "/usr/libexec/PlistBuddy", "-c", "Print :CFBundleIdentifier", infoPlist.absolutePath,
            ).redirectErrorStream(true).start()
            val actualIdentifier = readIdentifier.inputStream.bufferedReader().use { it.readText() }.trim()
            if (readIdentifier.waitFor() != 0) {
                throw GradleException("Unable to read packaged macOS application identifier: $actualIdentifier")
            }
            check(actualIdentifier == applicationIdentifier) {
                "Packaged macOS application identifier must be $applicationIdentifier, but was $actualIdentifier"
            }
            if (store) {
                val readMinimumVersion = ProcessBuilder(
                    "/usr/libexec/PlistBuddy", "-c", "Print :LSMinimumSystemVersion", infoPlist.absolutePath,
                ).redirectErrorStream(true).start()
                val minimumVersion = readMinimumVersion.inputStream.bufferedReader().use { it.readText() }.trim()
                check(readMinimumVersion.waitFor() == 0 && minimumVersion == macStoreMinimumSystemVersion) {
                    "Packaged Mac App Store application must require macOS $macStoreMinimumSystemVersion, " +
                        "but reported $minimumVersion"
                }
                val readVersions = ProcessBuilder(
                    "/usr/libexec/PlistBuddy", "-c", "Print :CFBundleShortVersionString",
                    "-c", "Print :CFBundleVersion", infoPlist.absolutePath,
                ).redirectErrorStream(true).start()
                val versions = readVersions.inputStream.bufferedReader().use { it.readLines() }
                val expected = listOf(packageVersion.get(), macStoreBuildVersion.get())
                check(readVersions.waitFor() == 0 && versions == expected) {
                    "Packaged Mac App Store application must report version and build $expected, but reported $versions"
                }
            }
            val readLocalNetworkDescription = ProcessBuilder(
                "/usr/libexec/PlistBuddy", "-c", "Print :NSLocalNetworkUsageDescription", infoPlist.absolutePath,
            ).redirectErrorStream(true).start()
            val actualLocalNetworkDescription = readLocalNetworkDescription.inputStream
                .bufferedReader().use { it.readText() }.trim()
            if (readLocalNetworkDescription.waitFor() != 0) {
                throw GradleException(
                    "Packaged macOS application is missing NSLocalNetworkUsageDescription: " +
                        actualLocalNetworkDescription
                )
            }
            check(actualLocalNetworkDescription == macLocalNetworkUsageDescription) {
                "Packaged macOS local-network usage description does not match the approved product text"
            }
        }
    }
}

// Use Windows Installer's related-product detection: the JDK helper can clear
// a valid upgrade result after failing to enumerate products (observed on ARM64).
// Also correct Liberica 21 ARM's x64 template before WiX compiles the installer.
val windowsInstallerResources = if (currentPlatform == "windows") {
    tasks.register("prepareWindowsInstallerResources") {
        val jpackageModule = packagingJavaLauncher.map {
            it.metadata.installationPath.file("jmods/jdk.jpackage.jmod")
        }
        val resources = layout.buildDirectory.dir("jpackage/windows-installer-resources")
        inputs.file(jpackageModule)
        inputs.property("applicationVersion", packageVersion)
        inputs.property("architecture", currentArchitecture)
        inputs.property("applicationIdentifier", applicationIdentifier)
        if (platformJpackageResources.asFile.isDirectory) inputs.dir(platformJpackageResources)
        outputs.dir(resources)
        doLast {
            val template = ZipFile(jpackageModule.get().asFile).use { archive ->
                val entry = archive.getEntry("classes/jdk/jpackage/internal/resources/main.wxs")
                    ?: throw GradleException("The selected JDK has no Windows MSI template")
                archive.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val platform = "Platform=\"x64\""
            val installerVersion = "<?define JpInstallerVersion=200 ?>"
            val productCode = "Id=\"\$(var.JpProductCode)\""
            check(template.split(platform).size == 2 && template.split(installerVersion).size == 2
                    && template.split(productCode).size == 2) {
                "The selected JDK MSI template changed; review the Windows ARM64 resource override"
            }
            val helperDefinition = "<CustomAction Id=\"JpFindRelatedProducts\" BinaryKey=\"JpCaDll\" DllEntry=\"FindRelatedProductsEx\" />"
            val helperSequence = "<Custom Action=\"JpFindRelatedProducts\" After=\"FindRelatedProducts\"/>"
            val helperDependency = "After=\"JpFindRelatedProducts\""
            check(template.split(helperDefinition).size == 2
                    && template.split(helperSequence).size == 3
                    && template.split(helperDependency).size == 3) {
                "The selected JDK MSI template changed; review related-product detection"
            }
            var installerTemplate = template.replace(helperDefinition, "")
                .replace(helperSequence, "<FindRelatedProducts />")
                .replace(helperDependency, "After=\"FindRelatedProducts\"")
            check(!installerTemplate.contains("JpFindRelatedProducts"))
            if (currentArchitecture == "arm64") {
                // Distinct architecture ProductCode; preserve the shared UpgradeCode.
                val armProductCode = UUID.nameUUIDFromBytes(
                    "$applicationIdentifier:${packageVersion.get()}:windows:arm64".toByteArray(Charsets.UTF_8)
                ).toString()
                installerTemplate = installerTemplate.replace(platform, "Platform=\"arm64\"")
                    .replace(installerVersion, "<?define JpInstallerVersion=500 ?>")
                    .replace(productCode, "Id=\"$armProductCode\"")
            }
            project.delete(resources)
            resources.get().asFile.mkdirs()
            if (platformJpackageResources.asFile.isDirectory) {
                project.copy { from(platformJpackageResources); into(resources) }
            }
            resources.get().file("main.wxs").asFile.writeText(
                installerTemplate,
                Charsets.UTF_8,
            )
        }
    }
} else null

fun registerInstallerTask(
    taskName: String,
    packageType: String,
    targetPlatform: String,
    platformOptions: List<String>,
): TaskProvider<Exec> {
    val destination = packagingOutputRoot.map {
        it.dir("$targetPlatform/installers/$packageType")
    }
    val cleanDestination = tasks.register<Delete>("clean${taskName.replaceFirstChar(Char::uppercaseChar)}") {
        delete(destination)
    }

    return tasks.register<Exec>(taskName) {
        group = "distribution"
        description = "Creates the $packageType FastRemote installer on $targetPlatform."
        dependsOn(packageAppImage, cleanDestination)
        if (targetPlatform == "windows" && windowsInstallerResources != null) {
            dependsOn(windowsInstallerResources)
            inputs.files(windowsInstallerResources)
        }

        inputs.dir(appImage)
        if (platformJpackageResources.asFile.isDirectory) inputs.dir(platformJpackageResources)
        inputs.property("applicationVersion", packageVersion)
        inputs.property("applicationDescription", applicationDescription)
        inputs.property("applicationVendor", applicationVendor)
        inputs.property("macSigningIdentity", macSigningIdentity.orElse(""))
        if (targetPlatform == "macos" && packageType == "dmg") inputs.file(signedMacDmgScript)
        outputs.dir(destination)

        doFirst {
            if (currentPlatform != targetPlatform) {
                throw GradleException(
                    "$taskName must run on $targetPlatform, but the current platform is $currentPlatform. " +
                        "jpackage does not support cross-platform installer generation."
                )
            }

            val outputDirectory = destination.get().asFile
            outputDirectory.mkdirs()

            val signingIdentity = macSigningIdentity.orNull.orEmpty()
            if (targetPlatform == "macos" && packageType == "dmg" && signingIdentity.isNotEmpty()) {
                val outputArtifact = outputDirectory.resolve(
                    "$applicationName-${packageVersion.get()}-macos-$macosArtifactArchitecture.dmg"
                )
                commandLine(
                    signedMacDmgScript.asFile.absolutePath,
                    appImage.get().asFile.absolutePath,
                    outputArtifact.absolutePath,
                    packagingJavaLauncher.get().metadata.installationPath
                        .file("jmods/jdk.jpackage.jmod").asFile.absolutePath,
                    platformJpackageResources.file("FastRemote-dmg-setup.scpt").asFile.absolutePath,
                    platformPackagingIcon.asFile.absolutePath,
                    applicationName,
                )
            } else {
                commandLine(buildList {
                    add(jpackageExecutable.get().absolutePath)
                    addAll(
                        listOf(
                            "--type", packageType,
                            "--dest", outputDirectory.absolutePath,
                            "--name", applicationName,
                            "--app-image", appImage.get().asFile.absolutePath,
                            "--app-version", packageVersion.get(),
                            "--vendor", applicationVendor,
                            "--description", applicationDescription,
                        )
                    )
                    addAll(platformOptions)
                    if (targetPlatform == "windows" && windowsInstallerResources != null) {
                        addAll(listOf("--resource-dir", layout.buildDirectory
                            .dir("jpackage/windows-installer-resources").get().asFile.absolutePath))
                    } else if (platformJpackageResources.asFile.isDirectory) {
                        addAll(listOf("--resource-dir", platformJpackageResources.asFile.absolutePath))
                    }
                })
            }
        }
    }
}

val packageDmg = registerInstallerTask(
    taskName = "packageDmg",
    packageType = "dmg",
    targetPlatform = "macos",
    platformOptions = emptyList(),
)

packageDmg.configure {
    doFirst {
        val process = ProcessBuilder("hdiutil", "info").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) {
            throw GradleException("Unable to inspect mounted disk images before DMG packaging: $output")
        }
        val mountedFastRemoteImages = Regex("(?m)^image-path\\s+:\\s+(.*/FastRemote[^/]*\\.dmg)\\s*$")
            .findAll(output).map { it.groupValues[1] }.distinct().toList()
        if (mountedFastRemoteImages.isNotEmpty()) {
            throw GradleException(
                "Unmount every FastRemote DMG before packageDmg so jpackage can create the Applications " +
                    "drag-install alias on the correct volume. Mounted images: " +
                    mountedFastRemoteImages.joinToString()
            )
        }
    }

    doLast {
        val outputDirectory = packagingOutputRoot.get().dir("macos/installers/dmg").asFile
        val standardName = "$applicationName-${packageVersion.get()}-macos-$macosArtifactArchitecture.dmg"
        val standardArtifact = outputDirectory.resolve(standardName)
        val generated = outputDirectory.listFiles { file ->
            file.isFile && file.extension.equals("dmg", ignoreCase = true) && file != standardArtifact
        }?.singleOrNull() ?: if (standardArtifact.isFile) standardArtifact else throw GradleException(
            "Expected exactly one generated DMG in ${outputDirectory.absolutePath}"
        )
        if (generated != standardArtifact) {
            Files.move(generated.toPath(), standardArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        val signingIdentity = macSigningIdentity.orNull.orEmpty()
        if (signingIdentity.isNotEmpty()) {
            val sign = ProcessBuilder(
                "codesign", "--force", "--sign", signingIdentity, "--timestamp",
                standardArtifact.absolutePath,
            ).redirectErrorStream(true).start()
            val signOutput = sign.inputStream.bufferedReader().use { it.readText() }
            if (sign.waitFor() != 0) {
                throw GradleException("Developer ID DMG signing failed: $signOutput")
            }
            val verify = ProcessBuilder(
                "codesign", "--verify", "--strict", "--verbose=2", standardArtifact.absolutePath,
            ).redirectErrorStream(true).start()
            val verifyOutput = verify.inputStream.bufferedReader().use { it.readText() }
            if (verify.waitFor() != 0) {
                throw GradleException("Developer ID DMG verification failed: $verifyOutput")
            }
        }

        val mountPoint = Files.createTempDirectory("fastremote-dmg-verify-")
        var attachedDevice: String? = null
        try {
            val attach = ProcessBuilder(
                "hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint",
                mountPoint.toString(), standardArtifact.absolutePath
            ).redirectErrorStream(true).start()
            val attachOutput = attach.inputStream.bufferedReader().use { it.readText() }
            if (attach.waitFor() != 0) {
                throw GradleException("Unable to mount generated DMG for drag-install verification: $attachOutput")
            }
            attachedDevice = attachOutput.lineSequence()
                .firstOrNull { line -> line.contains(mountPoint.toString()) }
                ?.trim()
                ?.substringBefore(' ')
                ?.substringBefore('\t')
                ?: Regex("(?m)^(/dev/disk\\d+)").findAll(attachOutput).lastOrNull()?.groupValues?.get(1)
                ?: throw GradleException("Unable to identify the mounted DMG volume device: $attachOutput")

            val packagedApp = mountPoint.resolve("$applicationName.app")
            val applicationsAlias = mountPoint.resolve("Applications")
            val finderMetadata = mountPoint.resolve(".DS_Store")
            val backgroundImage = mountPoint.resolve(".background/background.tiff")
            if (!Files.isDirectory(packagedApp)) {
                throw GradleException("Generated DMG does not contain $applicationName.app")
            }
            if (!Files.isRegularFile(applicationsAlias)) {
                throw GradleException(
                    "Generated DMG does not contain the Applications Finder alias required for drag installation"
                )
            }
            val file = ProcessBuilder("file", applicationsAlias.toString()).redirectErrorStream(true).start()
            val fileOutput = file.inputStream.bufferedReader().use { it.readText() }
            if (file.waitFor() != 0 || !fileOutput.contains("MacOS Alias file")) {
                throw GradleException(
                    "Generated DMG Applications entry is not a Finder alias: ${fileOutput.trim()}"
                )
            }
            if (!Files.isRegularFile(backgroundImage)) {
                throw GradleException("Generated DMG does not contain its Finder background image")
            }
            if (!Files.isRegularFile(finderMetadata) || Files.size(finderMetadata) == 0L) {
                throw GradleException(
                    "Generated DMG does not contain Finder layout metadata; background and icon layout would be lost"
                )
            }
            val finderMetadataText = Files.readString(finderMetadata, Charsets.ISO_8859_1)
            if (!finderMetadataText.contains("icvp") || !finderMetadataText.contains("Iloc")) {
                throw GradleException(
                    "Generated DMG Finder metadata does not contain icon-view properties and icon positions"
                )
            }
        } finally {
            attachedDevice?.let { device ->
                val detach = ProcessBuilder("hdiutil", "detach", device).redirectErrorStream(true).start()
                val detachOutput = detach.inputStream.bufferedReader().use { it.readText() }
                if (detach.waitFor() != 0) {
                    logger.warn("Unable to detach DMG verification device {}: {}", device, detachOutput.trim())
                }
            }
            Files.deleteIfExists(mountPoint)
        }
    }
}

val notarizeDmg = tasks.register<Exec>("notarizeDmg") {
    group = "distribution"
    description = "Submits the signed macOS DMG to Apple, staples its ticket, and validates Gatekeeper acceptance."
    dependsOn(packageDmg)
    outputs.upToDateWhen { false }

    doFirst {
        if (currentPlatform != "macos") {
            throw GradleException("notarizeDmg must run on macOS")
        }
        if (macSigningIdentity.orNull.orEmpty().isEmpty()) {
            throw GradleException("notarizeDmg requires -PmacSigningIdentity or FASTREMOTE_MAC_SIGNING_IDENTITY")
        }
        val profile = macNotaryProfile.orNull.orEmpty()
        if (profile.isEmpty()) {
            throw GradleException("notarizeDmg requires -PmacNotaryProfile or FASTREMOTE_MAC_NOTARY_PROFILE")
        }
        val artifact = packagingOutputRoot.get().dir("macos/installers/dmg")
            .file("$applicationName-${packageVersion.get()}-macos-$macosArtifactArchitecture.dmg").asFile
        if (!artifact.isFile) {
            throw GradleException("Signed DMG does not exist: ${artifact.absolutePath}")
        }
        commandLine(
            "xcrun", "notarytool", "submit", artifact.absolutePath,
            "--keychain-profile", profile, "--wait",
        )
    }

    doLast {
        val artifact = packagingOutputRoot.get().dir("macos/installers/dmg")
            .file("$applicationName-${packageVersion.get()}-macos-$macosArtifactArchitecture.dmg").asFile
        listOf(
            listOf("xcrun", "stapler", "staple", artifact.absolutePath),
            listOf("xcrun", "stapler", "validate", artifact.absolutePath),
            listOf(
                "spctl", "--assess", "--type", "open", "--context", "context:primary-signature",
                "--verbose=2", artifact.absolutePath,
            ),
        ).forEach { command ->
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor() != 0) {
                throw GradleException("Command failed (${command.joinToString(" ")}): $output")
            }
        }
    }
}

val packagePkg = registerInstallerTask(
    taskName = "packagePkg",
    packageType = "pkg",
    targetPlatform = "macos",
    platformOptions = emptyList(),
)

val macStoreInstallerIdentity = providers.environmentVariable("FASTREMOTE_MAC_STORE_INSTALLER_IDENTITY")
val macStoreIntermediateCertificate = providers.gradleProperty("macStoreIntermediateCertificate")
    .orElse("Apple Worldwide Developer Relations Certification Authority")
val packageMacAppStore = tasks.register<Exec>("packageMacAppStore") {
    group = "distribution"
    description = "Creates the signed, sandboxed macOS package for App Store Connect."
    dependsOn(packageAppImage)
    val artifact = packagingOutputRoot.map {
        it.file("macos/installers/store/FastRemote-${packageVersion.get()}-macos-store-$macosArtifactArchitecture.pkg")
    }
    inputs.dir(appImage)
    inputs.property("installerIdentity", macStoreInstallerIdentity.orElse(""))
    inputs.property("intermediateCertificate", macStoreIntermediateCertificate)
    outputs.file(artifact)
    doFirst {
        check(store && currentPlatform == "macos") {
            "packageMacAppStore requires macOS and -PfastremoteEdition=store"
        }
        check(macSigningIdentity.orNull.orEmpty().startsWith("3rd Party Mac Developer Application:")) {
            "Set FASTREMOTE_MAC_SIGNING_IDENTITY to the App Store application signing identity"
        }
        val installerIdentity = macStoreInstallerIdentity.orNull.orEmpty()
        check(installerIdentity.startsWith("3rd Party Mac Developer Installer:")) {
            "Set FASTREMOTE_MAC_STORE_INSTALLER_IDENTITY to the App Store installer signing identity"
        }
        val output = artifact.get().asFile
        output.parentFile.mkdirs()
        commandLine("productbuild", "--component", appImage.get().asFile.absolutePath, "/Applications",
            "--sign", installerIdentity, "--cert", macStoreIntermediateCertificate.get(), output.absolutePath)
    }
    doLast {
        val verify = ProcessBuilder("pkgutil", "--check-signature", artifact.get().asFile.absolutePath)
            .inheritIO().start()
        check(verify.waitFor() == 0) { "Mac App Store installer signature verification failed" }
    }
}

val windowsInstallerOptions = listOf(
    "--win-per-user-install",
    "--win-dir-chooser",
    "--win-menu",
    "--win-menu-group", applicationName,
    "--win-shortcut",
    "--win-upgrade-uuid", windowsUpgradeUuid,
)

val packageExe = registerInstallerTask(
    taskName = "packageExe",
    packageType = "exe",
    targetPlatform = "windows",
    platformOptions = windowsInstallerOptions,
)

val packageMsi = registerInstallerTask(
    taskName = "packageMsi",
    packageType = "msi",
    targetPlatform = "windows",
    platformOptions = windowsInstallerOptions,
)

packageMsi.configure {
    doFirst {
        check(!windowsCertificate.orNull.isNullOrBlank()) {
            "Windows MSI releases require FASTREMOTE_WINDOWS_CERTIFICATE_THUMBPRINT"
        }
        runWindowsAuthenticode(appImage.get().asFile, verifyOnly = true)
    }
    doLast {
        val outputDirectory = packagingOutputRoot.get().dir("windows/installers/msi").asFile
        val standardName = "$applicationName-${packageVersion.get()}-windows-$currentArchitecture.msi"
        val standardArtifact = outputDirectory.resolve(standardName)
        val generated = outputDirectory.listFiles { file ->
            file.isFile && file.extension.equals("msi", ignoreCase = true) && file != standardArtifact
        }?.singleOrNull() ?: if (standardArtifact.isFile) standardArtifact else throw GradleException(
            "Expected exactly one generated MSI in ${outputDirectory.absolutePath}"
        )
        if (generated != standardArtifact) {
            Files.move(generated.toPath(), standardArtifact.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        runWindowsAuthenticode(standardArtifact)

    }
}

val linuxInstallerOptions = listOf(
    "--linux-package-name", "fastremote",
    "--linux-app-category", "Network",
    "--linux-menu-group", "Network",
    "--linux-shortcut",
)

val packageDeb = registerInstallerTask(
    taskName = "packageDeb",
    packageType = "deb",
    targetPlatform = "linux",
    platformOptions = linuxInstallerOptions,
)

val packageRpm = registerInstallerTask(
    taskName = "packageRpm",
    packageType = "rpm",
    targetPlatform = "linux",
    platformOptions = linuxInstallerOptions,
)

val packagePortableTar = tasks.register<Tar>("packagePortableTar") {
    group = "distribution"
    description = "Creates the portable Linux application image as one tar.gz release artifact."
    dependsOn(packageAppImage)
    onlyIf { currentPlatform == "linux" }
    archiveFileName.set("$applicationName-${project.version}-linux-$currentArchitecture.tar.gz")
    destinationDirectory.set(packagingOutputRoot.map { it.dir("linux/installers/tar.gz") })
    compression = Compression.GZIP
    from(appImage) {
        into(applicationName)
        exclude { details ->
            details.relativePath.segments.any { segment -> segment.startsWith(".") }
        }
        eachFile {
            if (file.isFile && file.canExecute()) {
                permissions { unix("755") }
            }
        }
    }
    doFirst {
        if (currentPlatform != "linux") {
            throw GradleException("packagePortableTar must run on linux, but the current platform is $currentPlatform")
        }
    }
}

packagePkg.configure { mustRunAfter(packageDmg) }
packageMsi.configure { mustRunAfter(packageExe) }
packageRpm.configure { mustRunAfter(packageDeb) }

tasks.register("packageAll") {
    group = "distribution"
    description = "Builds the app image and every native installer supported by the current platform."

    when (currentPlatform) {
        "macos" -> if (store) dependsOn(packageMacAppStore) else dependsOn(packageDmg, packagePkg)
        "windows" -> dependsOn(packageExe, packageMsi)
        "linux" -> dependsOn(packageDeb, packageRpm, packagePortableTar)
    }
}

