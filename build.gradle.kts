plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "id.andriawan"
version = "1.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17") {
        exclude(group = "xml-apis", module = "xml-apis")
    }
    implementation("org.apache.xmlgraphics:batik-codec:1.17") {
        exclude(group = "xml-apis", module = "xml-apis")
    }
    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }

        changeNotes = """
            1.0.3
            - Add drag-and-drop similarity check (DropCheckDialog)
            - Add CompareAsset action
            - Improve DrawableScanService and DuplicateDrawablePanel
            - Update Gradle wrapper
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
