plugins {
    java
    `maven-publish`
}

group = "com.thebetterfolia"
version = findProperty("v")?.toString() ?: "1.0.0"

val pluginName: String = rootProject.name
val pluginVersion: String = project.version.toString()
val pluginGroup: String = project.group.toString()



repositories {
    maven("https://maven.aliyun.com/repository/public/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.26.1")
    compileOnly("io.netty:netty-buffer:4.2.7.Final")
    compileOnly("io.netty:netty-transport:4.2.7.Final")
    compileOnly("io.netty:netty-common:4.2.7.Final")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing"))
    }

    javadoc {
        options.encoding = "UTF-8"
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(
                "name" to pluginName,
                "version" to pluginVersion,
                "group" to pluginGroup
            )
        }
    }

    jar {
        archiveBaseName.set(pluginName)
        archiveVersion.set(pluginVersion)
    }

    register("release") {
        description = "Build release JAR. Usage: ./gradlew release -Pv=1.2.3"
        group = "build"
        dependsOn("clean", "jar")
        doLast {
            println("Built ${pluginName}-${project.version}.jar")
        }
    }

    register<Exec>("runServer") {
        description = "Build plugin and run test server"
        group = "application"
        dependsOn("jar")
        
        doFirst {
            val pluginsDir = file("test_env/plugins")
            pluginsDir.mkdirs()
            val pluginJar = file("build/libs/${pluginName}-${project.version}.jar")
            if (pluginJar.exists()) {
                pluginJar.copyTo(file("test_env/plugins/${pluginJar.name}"), overwrite = true)
                println("Copied plugin to plugins/")
            }
        }
        
        workingDir = file("test_env")
        commandLine("java", "-jar", "lophine-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar", "nogui")
        standardInput = System.`in`
    }
}
