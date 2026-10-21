plugins { java }

val androidSdk = providers.environmentVariable("ANDROID_HOME")
    .orElse("${System.getProperty("user.home")}/Android/Sdk")
val androidJar = androidSdk.map { "$it/platforms/android-36/android.jar" }
val d8 = configurations.create("d8")
val kotlinStdlib = configurations.create("kotlinStdlib") { isTransitive = false }

java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies {
    d8("com.android.tools:r8:9.5.28")
    kotlinStdlib("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    compileOnly(files(androidJar))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("junit:junit:4.13.2")
    testImplementation(files(androidJar))
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
}

tasks.register<JavaExec>("dex") {
    dependsOn(tasks.jar)
    val output = layout.buildDirectory.dir("dex")
    inputs.file(tasks.jar.flatMap { it.archiveFile })
    outputs.dir(output)
    doFirst { output.get().asFile.mkdirs() }
    classpath = d8
    mainClass = "com.android.tools.r8.D8"
    args(
        "--release", "--min-api", "28",
        "--lib", androidJar.get(), "--classpath", kotlinStdlib.singleFile,
        "--output", output.get().asFile, tasks.jar.get().archiveFile.get().asFile,
    )
}

// Robolectric uses AndroidX test libraries published as AARs; a plain Java project needs their jars.
val testAarDirectory = layout.buildDirectory.dir("test-aar-jars")
val extractTestAars = tasks.register("extractTestAars") {
    val aars = configurations.testRuntimeClasspath.map { files -> files.filter { it.extension == "aar" } }
    inputs.files(aars)
    outputs.dir(testAarDirectory)
    doLast {
        aars.get().forEach { archive ->
            copy {
                from(zipTree(archive)) { include("classes.jar"); rename { "${archive.nameWithoutExtension}.jar" } }
                into(testAarDirectory)
            }
        }
    }
}
tasks.test {
    dependsOn(extractTestAars)
    classpath += files(testAarDirectory).asFileTree.matching { include("*.jar") }
}
