plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
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
    implementation(libs.imageio.webp)
    implementation(libs.batik.transcoder) {
        exclude(group = "xml-apis", module = "xml-apis")
    }
    implementation(libs.batik.codec) {
        exclude(group = "xml-apis", module = "xml-apis")
    }
    testImplementation(libs.junit)
    intellijPlatform {
        intellijIdea(libs.versions.intellijIdea)
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
