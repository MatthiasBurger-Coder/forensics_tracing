plugins {
    `java-library`
}

dependencies {
    implementation(project(":forensics-domain"))
    implementation(libs.slf4j.api)

    testImplementation(project(":forensics-domain"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
