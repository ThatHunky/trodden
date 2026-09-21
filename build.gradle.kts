plugins {
    `java-library`
}

group = "dev.thathunky"
version = "1.1.0"

// JDK 25 compiles (it has to read paper-api 26.x, whose classes are Java 25), but the output is
// Java 21 bytecode: 1.21.x servers run on Java 21 and would refuse anything newer. Gradle would
// otherwise take release 21 to mean "only Java 21 libraries" and reject paper-api 26.x, which is
// compiled for 25 but only ever compiled against, never run by the Java 21 servers.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    disableAutoTargetJvm()
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
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Compile checks: the plugin's own sources against the paper-api of every supported minor line.
// A method or constant the oldest server lacks fails here instead of with NoSuchMethodError on a
// live server. Runs as part of `check`. The boot test on real servers is .github/workflows/compat.yml.
val compatApis = linkedMapOf(
    "1.21.4" to "1.21.4-R0.1-SNAPSHOT",
    "1.21.8" to "1.21.8-R0.1-SNAPSHOT",
    "1.21.11" to "1.21.11-R0.1-SNAPSHOT",
    "26.1.2" to "26.1.2.build.74-stable",
    "26.2" to "26.2.build.121-stable",
    "26.3" to "26.3.build.30-alpha",
)

val compatChecks = compatApis.map { (mc, api) ->
    val id = mc.replace('.', '_')
    val cp = configurations.create("compat_$id") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(configurations["compileOnly"])
        exclude(group = "io.papermc.paper", module = "paper-api")
    }
    // Added after the exclude via a separate, non-excluded configuration so exactly this version wins.
    val paper = configurations.create("compatPaper_$id") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(paper.name, "io.papermc.paper:paper-api") {
        version { strictly(api) }
    }
    tasks.register<JavaCompile>("compatCompile_$id") {
        group = "verification"
        description = "Compiles the plugin against paper-api $api (Minecraft $mc)."
        source = fileTree("src/main/java")
        classpath = paper + cp
        destinationDirectory.set(layout.buildDirectory.dir("compat/$id"))
        javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    }
}

val compatCheck = tasks.register("compatCheck") {
    group = "verification"
    description = "Compiles the plugin against every supported paper-api version."
    dependsOn(compatChecks)
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
    dependsOn(runTests, compatCheck)
}
