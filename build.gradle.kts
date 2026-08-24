plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.5.1"
    id("xyz.jpenilla.run-velocity") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/") // bStats
}

val velocityVersion = "4.0.0-SNAPSHOT"

dependencies {
    // Velocity platform API - provided by the proxy at runtime, never shaded
    compileOnly("com.velocitypowered:velocity-api:$velocityVersion")

    // LuckPerms API - provided by the LuckPerms plugin at runtime, never shaded. Used to grant/
    // revoke the "easybans.muted" network-wide permission node (see mute/MuteNetworkSync.java) -
    // the source of truth backends check instead of relying on Velocity's PlayerChatEvent, which
    // can't safely cancel signed (1.19.1+) chat without a companion plugin on every server.
    compileOnly("net.luckperms:api:5.5")

    // Connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // JDBC drivers: H2 (default, embedded), MariaDB client (also speaks the MySQL wire
    // protocol, so it backs both the "mysql" and "mariadb" storage types - see
    // ARCHITECTURE.md), PostgreSQL
    implementation("com.h2database:h2:2.2.224")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    implementation("org.postgresql:postgresql:42.7.4")

    // Config / serialization
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.code.gson:gson:2.11.0")

    // Optional multi-instance sync backend (used only if sync.mode: redis)
    implementation("redis.clients:jedis:5.2.0")

    // Anonymous usage statistics: https://bstats.org/plugin/velocity/BetterBans/32581
    implementation("org.bstats:bstats-velocity:3.1.0")

    // Needed on the test classpath (but never shaded) so tests can exercise MessageService,
    // which uses Adventure/MiniMessage types bundled with velocity-api.
    testImplementation("com.velocitypowered:velocity-api:$velocityVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Only the shaded jar should end up in build/libs - the plain (unshaded) jar the "jar" task
    // would otherwise also produce there is useless on its own (Velocity plugins need their
    // relocated dependencies bundled in) and would just be a second, confusing artifact.
    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    runVelocity {
        // The run-velocity plugin downloads a released proxy build to test against, so the
        // "-SNAPSHOT" suffix (only meaningful for the Maven dependency coordinate above) is
        // stripped here.
        velocityVersion(velocityVersion.removeSuffix("-SNAPSHOT"))
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }

    shadowJar {
        // No "-all" classifier: this is the only jar this build produces.
        archiveClassifier.set("")

        // Without this, same-named META-INF/services files from different jars (e.g. each
        // JDBC driver's own META-INF/services/java.sql.Driver) get deduplicated *before*
        // mergeServiceFiles() can combine them, silently leaving only one driver registered.
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        // Relocate every shaded dependency so this plugin never collides with another plugin
        // bundling a different version of the same library.
        val shadedPackage = "fr.mathilde.easybans.libs"
        relocate("com.zaxxer.hikari", "$shadedPackage.hikari")
        relocate("org.yaml.snakeyaml", "$shadedPackage.snakeyaml")
        relocate("com.google.gson", "$shadedPackage.gson")
        relocate("org.h2", "$shadedPackage.h2")
        relocate("org.mariadb.jdbc", "$shadedPackage.mariadb")
        relocate("org.postgresql", "$shadedPackage.postgresql")
        relocate("redis.clients.jedis", "$shadedPackage.jedis")
        relocate("org.apache.commons.pool2", "$shadedPackage.commonspool2")
        relocate("org.bstats", "$shadedPackage.bstats")
        mergeServiceFiles()
    }
}
