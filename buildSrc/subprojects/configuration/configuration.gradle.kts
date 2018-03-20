plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":kotlinDsl"))
}

gradlePlugin {
    (plugins) {
        "availableJavaInstallations" {
            id = "gradlebuild.available-java-installations"
            implementationClass = "org.gradle.gradlebuild.java.AvailableJavaInstallationsPlugin"
        }
        "dependenciesMetadataRules" {
            id = "gradlebuild.dependencies-metadata-rules"
            implementationClass = "org.gradle.gradlebuild.dependencies.DependenciesMetadataRulesPlugin"
        }
    }
}
