plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
}

group = "id.andriawan"
version = "1.1.1"

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
            <b>1.1.1</b>
            <ul>
                <li>Results are grouped: every copy of an asset is one row instead of one row per pair</li>
                <li>Delete redundant copies from the tool window via the IDE's Safe Delete refactoring</li>
                <li>Rebuilt tool window as a group list with per-copy previews and actions</li>
                <li>Live similarity slider plus module and format filters, with no rescan</li>
                <li>External file checks now open in the tool window instead of a separate dialog</li>
                <li>Results survive closing and reopening the tool window</li>
                <li>Image decoding no longer holds the read lock, and now runs in parallel</li>
            </ul>
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
