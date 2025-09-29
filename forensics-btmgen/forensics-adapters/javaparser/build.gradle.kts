plugins {
    `java-library`
}

dependencies {
    implementation(project(":forensics-domain"))
    implementation(libs.javaparser.symbol.solver.core)

    testImplementation(project(":forensics-domain"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
