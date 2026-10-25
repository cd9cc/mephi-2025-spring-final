plugins {
    `java-library`
}

dependencies {
    api("jakarta.validation:jakarta.validation-api")

    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    compileOnly("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework:spring-tx")
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-ui")
}
