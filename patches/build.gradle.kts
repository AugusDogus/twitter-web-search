plugins { kotlin("jvm") }

val androidSdk = providers.environmentVariable("ANDROID_HOME")
    .orElse("${System.getProperty("user.home")}/Android/Sdk")
val cli = rootProject.file("deps/morphe-desktop-1.15.0-all.jar")
val d8 = configurations.create("d8")

kotlin { jvmToolchain(21); compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies {
    d8("com.android.tools:r8:9.5.28")
    compileOnly(files(cli))
    testImplementation(files(cli))
    testImplementation(kotlin("test"))
}

val extensionDex = project(":extension").layout.buildDirectory.file("dex/classes.dex")
tasks.processResources {
    dependsOn(":extension:dex")
    from(extensionDex) { into("extensions"); rename { "web-search.dex" } }
}
tasks.jar {
    manifest.attributes(
        "Name" to "NewX Web Search",
        "Description" to "Replace Ask Grok in post text selection with Android web search",
        "Version" to project.version,
        "License" to "GPL-3.0-or-later",
        "Patcher-Version" to "1.12.0",
    )
    from(rootProject.file("LICENSE")) { into("META-INF") }
}
val codeJar = tasks.register<Jar>("codeJar") {
    archiveClassifier = "code"
    from(sourceSets.main.map { it.output.classesDirs })
}
val patchDex = tasks.register<JavaExec>("patchDex") {
    dependsOn(codeJar)
    val output = layout.buildDirectory.dir("dex")
    inputs.file(codeJar.flatMap { it.archiveFile })
    outputs.dir(output)
    doFirst { output.get().asFile.mkdirs() }
    classpath = d8
    mainClass = "com.android.tools.r8.D8"
    args(
        "--release", "--min-api", "26",
        "--lib", "${androidSdk.get()}/platforms/android-36/android.jar",
        "--classpath", cli, "--output", output.get().asFile,
        codeJar.get().archiveFile.get().asFile,
    )
}
val bundle = tasks.register<Zip>("bundle") {
    dependsOn(patchDex)
    archiveFileName = "newx-web-search-${project.version}.mpp"
    destinationDirectory = layout.buildDirectory.dir("libs")
    from(tasks.jar.map { zipTree(it.archiveFile) })
    from(layout.buildDirectory.dir("dex"))
}
tasks.build { dependsOn(bundle) }
tasks.test { useJUnitPlatform() }
