import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val checkstyleVersion = libsCatalog.findVersion("checkstyle").get().requiredVersion

extensions.configure<CheckstyleExtension>("checkstyle") {
    toolVersion = checkstyleVersion
    configDirectory.set(layout.projectDirectory.dir("config/checkstyle"))
    isIgnoreFailures = false
    maxErrors = 0
    maxWarnings = 0
}

val generatedQuerydslPath = layout.buildDirectory.dir("generated/querydsl").get().asFile.toPath()

tasks.withType<Checkstyle>().configureEach {
    exclude { it.file.toPath().startsWith(generatedQuerydslPath) }
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
