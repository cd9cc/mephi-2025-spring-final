dependencies {
    testImplementation(project(":common"))
    testImplementation(project(":hotel-service"))
    testImplementation(project(":booking-service"))
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}

tasks.processTestResources {
    listOf("hotel-service", "booking-service").forEach { service ->
        from(project(":$service").file("src/main/resources/application.yml")) {
            rename { "$service.yml" }
        }
    }
}
