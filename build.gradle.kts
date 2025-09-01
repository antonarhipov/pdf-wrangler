plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.example"
version = "0.0.1-SNAPSHOT"
description = "pdf-wrangler"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // PDF processing dependencies
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.pdfbox:fontbox:3.0.3")
    implementation("org.apache.pdfbox:pdfbox-tools:3.0.3")
    
    // Image processing dependencies
    implementation("com.github.jai-imageio:jai-imageio-core:1.4.0")
    implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.10.1")
    implementation("com.twelvemonkeys.imageio:imageio-tiff:3.10.1")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.10.1")
    
    // LibreOffice integration dependencies
    implementation("org.jodconverter:jodconverter-spring-boot-starter:4.4.7")
    implementation("org.jodconverter:jodconverter-local:4.4.7")
    
    // OCR dependencies
    implementation("net.sourceforge.tess4j:tess4j:5.9.0")
    
    // Structured logging dependencies
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("ch.qos.logback.contrib:logback-json-classic:0.1.5")
    implementation("ch.qos.logback.contrib:logback-jackson:0.1.5")
    
    // Validation dependencies
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
//    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
