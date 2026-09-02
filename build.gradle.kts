plugins {
    java
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.4.3"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "me.bounser"
version = "1.9.9"

java { sourceCompatibility = JavaVersion.VERSION_25; targetCompatibility = JavaVersion.VERSION_25 }
kotlin { jvmToolchain(25) }
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/"); maven("https://oss.sonatype.org/content/groups/public/"); maven("https://maven.respark.dev/releases"); maven("https://repo.extendedclip.com/content/repositories/placeholderapi/"); maven("https://m2.dv8tion.net/releases"); maven("https://nexus.scarsz.me/content/groups/public/"); maven("https://repo.codemc.io/repository/maven-snapshots/"); maven("https://jitpack.io"); maven("https://repo.xenondevs.xyz/releases"); maven("https://repo.codemc.io/repository/maven-public/") }
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("xyz.xenondevs.invui:invui:2.1.0")
    implementation("io.javalin:javalin:6.6.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    compileOnly("jfree:jfreechart:1.0.13"); compileOnly("me.leoko.advancedgui:AdvancedGUI:2.2.8"); compileOnly("me.clip:placeholderapi:2.11.6"); compileOnly("org.xerial:sqlite-jdbc:3.43.0.0"); compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { exclude(group = "org.bukkit", module = "bukkit") }
    compileOnly("com.discordsrv:discordsrv:1.28.0"); compileOnly("commons-io:commons-io:2.14.0"); compileOnly("net.dv8tion:JDA:5.0.0-beta.18"); compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("net.wesjd:anvilgui:1.10.4-SNAPSHOT")
    compileOnly("redis.clients:jedis:5.1.2"); implementation("org.mindrot:jbcrypt:0.4"); implementation("de.tr7zw:item-nbt-api:2.15.5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3"); testRuntimeOnly("org.junit.platform:junit-platform-launcher"); testImplementation("org.xerial:sqlite-jdbc:3.43.0.0"); testImplementation("org.mockito:mockito-core:5.23.0"); testImplementation("org.mockito:mockito-junit-jupiter:5.23.0"); testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}
val upstreamWebJar = layout.buildDirectory.file("upstream/Nascraft-1.9.1.jar"); val originalWebResources = layout.buildDirectory.dir("generated/original-web-resources")
val restoreOriginalWebResources by tasks.registering { outputs.dir(originalWebResources); doLast { val jarFile = upstreamWebJar.get().asFile; jarFile.parentFile.mkdirs(); if (!jarFile.exists()) { val source = uri("https://github.com/Bounser/Nascraft/releases/download/v1.9.1/Nascraft-1.9.1.jar").toURL(); source.openStream().use { input -> jarFile.outputStream().use { output -> input.copyTo(output) } } }; require(jarFile.length() == 27_486_714L); val outputDir = originalWebResources.get().asFile; delete(outputDir); copy { from(zipTree(jarFile)); into(outputDir); include("web/index.html"); include("web/style.css"); include("web/script.js"); include("images/logo.png"); include("images/logo-color.png") } } }
tasks {
    processResources { val props = mapOf("version" to project.version); inputs.properties(props); exclude("web-original-1.9.1.zip"); filesMatching("plugin.yml") { expand(props) } }
    jar { archiveClassifier.set("plain") }
    shadowJar {
        archiveClassifier.set(""); archiveBaseName.set("Nascraft")
        dependencies { exclude(dependency("org.xerial:sqlite-jdbc:.*")); exclude(dependency("net.dv8tion:JDA:.*")); exclude(dependency("jfree:jfreechart:.*")); exclude(dependency("com.zaxxer:HikariCP:.*")); exclude(dependency("redis.clients:jedis:.*")); exclude(dependency("org.jetbrains.kotlin:.*:.*")) }
        relocate("org.bstats", "me.bounser.bstats"); relocate("net.wesjd.anvilgui", "me.bounser.anvilgui"); relocate("de.tr7zw.changeme.nbtapi", "me.bounser.nbtapi"); relocate("io.javalin", "me.bounser.web.libs.javalin"); relocate("com.fasterxml.jackson", "me.bounser.web.libs.jackson")
    }
    test { useJUnitPlatform() }
    build { dependsOn(shadowJar) }
    runServer { minecraftVersion("26.2"); jvmArgs("-Dcom.mojang.eula.agree=true"); downloadPlugins { github("milkbowl", "Vault", "1.7.3", "Vault.jar"); hangar("PlaceholderAPI", "2.11.6"); github("EssentialsX", "Essentials", "2.21.2", "EssentialsX-2.21.2.jar") } }
}