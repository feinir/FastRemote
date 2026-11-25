// SPDX-License-Identifier: MPL-2.0
plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://repo.huaweicloud.com/repository/maven")
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/gradle-plugin")
    gradlePluginPortal()
    mavenCentral()
}
