// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    configurations.all {
        resolutionStrategy {
            force("com.google.code.findbugs:jsr305:3.0.2")
            force("org.jetbrains:annotations:13.0")
            force("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
        }
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
