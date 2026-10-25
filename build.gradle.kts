plugins {
    java
    id("org.springframework.boot") version "3.5.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

val springBootVersion = "3.5.7"
val springCloudVersion = "2025.0.0"
val mapstructVersion = "1.6.3"
val springdocVersion = "2.8.13"

val libraryModules = listOf(project(":common"))
val testModules = listOf(project(":e2e-tests"))
val businessServices = listOf(project(":hotel-service"), project(":booking-service"))

allprojects {
    group = "ru.mephi.hotelbooking"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        }
        dependencies {
            dependency("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    the<CheckstyleExtension>().apply {
        toolVersion = "10.26.1"
        maxWarnings = 0
    }
}

configure(subprojects - libraryModules - testModules) {
    apply(plugin = "org.springframework.boot")

    dependencies {
        "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    }
}

configure(subprojects - libraryModules - testModules - businessServices) {
    tasks.named<Jar>("jar") {
        enabled = false
    }
}

configure(businessServices) {
    dependencies {
        "implementation"(project(":common"))
        "implementation"("org.springframework.boot:spring-boot-starter-web")
        "implementation"("org.springframework.boot:spring-boot-starter-validation")
        "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
        "implementation"("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
        "implementation"("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
        "implementation"("org.springdoc:springdoc-openapi-starter-webmvc-ui")
        "runtimeOnly"("com.h2database:h2")
        "testImplementation"("org.springframework.security:spring-security-test")

        "implementation"("org.mapstruct:mapstruct:$mapstructVersion")
        "annotationProcessor"("org.mapstruct:mapstruct-processor:$mapstructVersion")
        "annotationProcessor"("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(
            listOf(
                "-Amapstruct.defaultComponentModel=spring",
                "-Amapstruct.unmappedTargetPolicy=ERROR",
            )
        )
    }
}
