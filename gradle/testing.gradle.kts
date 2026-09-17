import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.testing.jacoco.tasks.JacocoReport

val requireAllTests = providers.environmentVariable("CI").map { it.equals("true", ignoreCase = true) }.getOrElse(false) ||
    providers.gradleProperty("requireAllTests").map { it.toBooleanStrict() }.getOrElse(false)

val mainResources = extensions.getByType<SourceSetContainer>().named("main").get().resources
val checkSensitiveResources by tasks.registering {
    group = "verification"
    description = "Fails when environment files are included in application resources."

    val sensitiveResources = mainResources.matching {
        include(".env", ".env.*", "**/.env", "**/.env.*")
    }
    inputs.files(sensitiveResources)

    doLast {
        if (!sensitiveResources.isEmpty) {
            throw GradleException("Environment files must not be packaged as resources.")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "1g"
    dependsOn(checkSensitiveResources)
    inputs.property("requireAllTests", requireAllTests)

    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit

        override fun beforeTest(testDescriptor: TestDescriptor) = Unit

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                logger.lifecycle(
                    "Tests: ${result.testCount}, passed: ${result.successfulTestCount}, " +
                        "failed: ${result.failedTestCount}, skipped: ${result.skippedTestCount}"
                )
                if (requireAllTests && (result.testCount == 0L || result.skippedTestCount > 0L)) {
                    throw GradleException(
                        "All tests must run: ${result.skippedTestCount} skipped out of ${result.testCount}. " +
                            "Check Docker availability and disabled tests."
                    )
                }
            }
        }
    })
}

tasks.named<Test>("test") {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
