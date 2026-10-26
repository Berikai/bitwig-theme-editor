plugins {
    id("java")
}

group = "dev.berikai.BitwigTheme"
version = "2.3.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.formdev:flatlaf:3.6.1")
    implementation("com.formdev:flatlaf-intellij-themes:3.6.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("com.intellij:forms_rt:7.0.3")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        manifest {
            attributes["Main-Class"] = "dev.berikai.BitwigTheme.Main"
            attributes["Implementation-Version"] = project.version
        }

        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}