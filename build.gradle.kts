plugins {
    id("java")
}

group = "dev.berikai.BitwigTheme"
version = "1.4.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.formdev:flatlaf:3.4.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        manifest {
            attributes["Main-Class"] = "dev.berikai.BitwigTheme.Main"
        }

        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}