// SPDX-License-Identifier: MPL-2.0
pluginManagement {
    includeBuild("build-logic")

    repositories {
        maven("https://repo.huaweicloud.com/repository/maven") {
            name = "HuaweiCloudMavenMirror"
        }
        maven("https://maven.aliyun.com/repository/public") {
            name = "AliyunPublicMirror"
        }
        maven("https://maven.aliyun.com/repository/gradle-plugin") {
            name = "AliyunGradlePluginMirror"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // The Node Gradle plugin registers the same narrowly scoped Ivy repository at project level.
    // Prefer this settings-owned declaration so all dependency sources remain auditable here.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        maven("https://repo.huaweicloud.com/repository/maven") {
            name = "HuaweiCloudMavenMirror"
        }
        maven("https://maven.aliyun.com/repository/public") {
            name = "AliyunPublicMirror"
        }
        mavenCentral()
        ivy {
            name = "EclipseTm4eRelease"
            url = uri("https://download.eclipse.org/tm4e/releases/0.17.2")
            patternLayout {
                artifact("plugins/[artifact]_[revision].[ext]")
            }
            metadataSources { artifact() }
            content {
                includeGroup("org.eclipse.tm4e")
            }
        }
    }
}

rootProject.name = "FastRemote"

include(
    "fastremote-domain",
    "fastremote-i18n",
    "fastremote-transport",
    "fastremote-terminal",
    "fastremote-storage",
    "fastremote-licensing",
    "fastremote-ai",
    "fastremote-mcp",
    "fastremote-ui",
    "fastremote-app",
    "jeditermfx-core",
    "jeditermfx-ui",
    "richtextfx",
)

project(":jeditermfx-core").projectDir = file("JediTermFX/jeditermfx-core")
project(":jeditermfx-ui").projectDir = file("JediTermFX/jeditermfx-ui")

// Keep the integration recipe versioned with FastRemote, not in the ignored fork checkout.
project(":jeditermfx-ui").buildFileName = "../../gradle/jeditermfx-ui.gradle.kts"

project(":richtextfx").projectDir = file("RichTextFX/richtextfx")
project(":richtextfx").buildFileName = "../../gradle/richtextfx.gradle.kts"
