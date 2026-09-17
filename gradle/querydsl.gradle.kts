import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile

val querydslDir = layout.buildDirectory.dir("generated/querydsl")

extensions.configure<SourceSetContainer>("sourceSets") {
    named("main") {
        java.srcDir(querydslDir)
        resources.exclude(".env", ".env.*", "**/.env", "**/.env.*")
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Xlint:deprecation")
    options.generatedSourceOutputDirectory.set(querydslDir)
}
