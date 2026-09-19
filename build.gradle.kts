import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.1.0"
}

group = "dev.thathunky"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") // Paper API
    maven("https://maven.enginehub.org/repo/") // WorldGuard, WorldEdit (sk89q)
    maven("https://jitpack.io") // GriefPrevention, LandsAPI
    maven("https://repo.glaremasters.me/repository/towny/") // Towny
    maven("https://repo.william278.net/releases") // HuskClaims
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")

    // Claim-plugin APIs. Only HuskClaims has been run against a live server (see README);
    // the rest compile against their published APIs but are otherwise untested by us.
    compileOnly("net.william278.huskclaims:huskclaims-bukkit:1.5.10")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.5")
    compileOnly("com.github.TechFortress:GriefPrevention:16.18.7")
    compileOnly("com.github.Angeschossen:LandsAPI:7.25.4")
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.7")

    // Shaded and relocated into the final jar; see Metrics.java and the README for how to disable it.
    implementation("org.bstats:bstats-bukkit:3.2.1")

    // implementation, not compileOnly, so TestMain has paper-api on its runtime classpath too —
    // build.sh's classpath already includes it (from libraries/) for the same reason.
    testImplementation("io.papermc.paper:paper-api:26.2.build.121-stable")
}

sourceSets {
    main {
        resources {
            srcDir(rootDir)
            include("plugin.yml", "config.yml", "lang/**")
        }
        // bStats lives here, not under src/main/java, so build.sh's plain javac (which only ever
        // looks at src/main/java) neither needs org.bstats on its classpath nor bundles it. Only
        // this Gradle build compiles and shades it in. See PluginMetrics's javadoc.
        java {
            srcDir("src/bstats/java")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// TestMain is a plain main() with System.exit(1) on failure, not a JUnit suite, so it is run as a
// JavaExec rather than through the standard `test` task. A non-zero exit fails this task, and thus
// `check` and `build`, exactly like build.sh's own run of the same class.
val runTests = tasks.register<JavaExec>("runTests") {
    group = "verification"
    description = "Runs the server-free TestMain checks (same checks build.sh runs)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.thathunky.trodden.TestMain")
}

// The default `test` task expects a JUnit-style suite; TestMain is a plain main() class instead,
// run above by `runTests`. Disable `test` rather than fight it into discovering nothing.
tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn(runTests)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    relocate("org.bstats", "dev.thathunky.trodden.libs.bstats")
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.jar {
    archiveClassifier.set("plain")
}
