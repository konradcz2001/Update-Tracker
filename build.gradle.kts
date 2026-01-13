plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "1.8.15"
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("org.beryx.jlink") version "2.25.0"
}

group = "com.github.konradcz2001"
version = "1.1.0"

repositories {
    mavenCentral()
}

val junitVersion = "5.12.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-module")
}

application {
    mainModule.set("com.github.konradcz2001.updatetracker")
    mainClass.set("com.github.konradcz2001.updatetracker.Launcher")
}

javafx {
    version = "21.0.6"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

dependencies {
    // Scraping library
    implementation("org.jsoup:jsoup:1.17.2")
    // JSON processing library
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.processResources {
    // Replace ${project.version} in app.properties with the actual Gradle version
    filesMatching("**/app.properties") {
        expand("project" to project)
    }
}

jlink {
    imageZip.set(layout.buildDirectory.file("/distributions/UpdateTracker-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages", "--add-modules", "jdk.crypto.ec,java.logging"))

    launcher {
        name = "UpdateTracker"
        noConsole = true
    }

    jpackage {
        imageName = "UpdateTracker"
        installerName = "UpdateTrackerSetup"
        appVersion = "1.1.0"

        installerType = "msi"

        icon = "src/main/resources/app_icon.ico"

        if (org.gradle.internal.os.OperatingSystem.current().isWindows()) {
            installerOptions.add("--win-dir-chooser")
            installerOptions.add("--win-shortcut")
            installerOptions.add("--win-menu")

            installerOptions.add("--win-menu-group")
            installerOptions.add("Update Tracker")

        }
    }
}
