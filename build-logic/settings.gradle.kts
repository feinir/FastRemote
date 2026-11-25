// SPDX-License-Identifier: MPL-2.0
pluginManagement {
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

rootProject.name = "fastremote-build-logic"
