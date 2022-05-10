/*
 * Build files for the XDK.
 */

val javatools     = project(":javatools")
val turtle        = project(":javatools_turtle")
val bridge        = project(":javatools_bridge")
val ecstasy       = project(":lib_ecstasy")
val aggregate     = project(":lib_aggregate");
val collections   = project(":lib_collections");
val crypto        = project(":lib_crypto");
val net           = project(":lib_net");
val json          = project(":lib_json");
val oodb          = project(":lib_oodb");
val imdb          = project(":lib_imdb");
val jsondb        = project(":lib_jsondb");
val web           = project(":lib_web");
val platform      = project(":lib_platform");
val host          = project(":lib_host");
val hostControl   = project(":lib_hostControl");

val ecstasyMain     = "${ecstasy.projectDir}/src/main"
val turtleMain      = "${turtle.projectDir}/src/main"
val bridgeMain      = "${bridge.projectDir}/src/main"
val javatoolsJar    = "${javatools.buildDir}/libs/javatools.jar"
val aggregateMain   = "${aggregate.projectDir}/src/main";
val collectionsMain = "${collections.projectDir}/src/main";
val cryptoMain      = "${crypto.projectDir}/src/main";
val netMain         = "${net.projectDir}/src/main";
val jsonMain        = "${json.projectDir}/src/main";
val oodbMain        = "${oodb.projectDir}/src/main";
val imdbMain        = "${imdb.projectDir}/src/main";
val jsondbMain      = "${jsondb.projectDir}/src/main";
val webMain         = "${web.projectDir}/src/main";
val platformMain    = "${platform.projectDir}/src/main";
val hostMain        = "${host.projectDir}/src/main";
val hostControlMain = "${hostControl.projectDir}/src/main";

val libDir          = "$buildDir/xdk/lib"
val javaDir         = "$buildDir/xdk/javatools"

val coreLib         = "$libDir/ecstasy.xtc"
val turtleLib       = "$javaDir/javatools_turtle.xtc"
val bridgeLib       = "$javaDir/javatools_bridge.xtc"

val version         = "0.3-alpha"

tasks.register("clean") {
    group       = "Build"
    description = "Delete previous build results"
    delete("$buildDir")
}

val copyOutline = tasks.register<Copy>("copyOutline") {
    from("$projectDir/src/main/resources") {
        include("xdk/**")
    }
    into("$buildDir")
    doLast {
        println("Finished task: copyOutline")
    }
}

val copyJavatools = tasks.register<Copy>("copyJavatools") {
    from(javatoolsJar)
    into("$buildDir/xdk/javatools/")

    dependsOn(javatools.tasks["build"])
    dependsOn(copyOutline)
    doLast {
        println("Finished task: copyJavatools")
    }
}

val compileEcstasy = tasks.register<JavaExec>("compileEcstasy") {
    group       = "Build"
    description = "Build ecstasy.xtc and javatools_turtle.xtc modules"

    dependsOn(javatools.tasks["build"])
    dependsOn(copyJavatools)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "$ecstasyMain/x/ecstasy.x",
            "$turtleMain/x/mack.x")
    mainClass.set("org.xvm.tool.Compiler")

    doLast {
        file("$libDir/mack.xtc").
           renameTo(file("$turtleLib"))
        println("Finished task: compileEcstasy")
    }
}

val compileAggregate = tasks.register<JavaExec>("compileAggregate") {
    group       = "Build"
    description = "Build aggregate.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileCollections)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "$aggregateMain/x/aggregate.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileCollections = tasks.register<JavaExec>("compileCollections") {
    group       = "Build"
    description = "Build collections.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileEcstasy)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "$collectionsMain/x/collections.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileCrypto = tasks.register<JavaExec>("compileCrypto") {
    group       = "Build"
    description = "Build crypto.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileEcstasy)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "$cryptoMain/x/crypto.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileNet = tasks.register<JavaExec>("compileNet") {
    group       = "Build"
    description = "Build net.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileCrypto)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "-L", "$libDir",
            "$netMain/x/net.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileJson = tasks.register<JavaExec>("compileJson") {
    group       = "Build"
    description = "Build json.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileEcstasy)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "$jsonMain/x/json.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileOODB = tasks.register<JavaExec>("compileOODB") {
    group       = "Build"
    description = "Build oodb.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileEcstasy)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "$oodbMain/x/oodb.x")
   mainClass.set("org.xvm.tool.Compiler")
}

val compileIMDB = tasks.register<JavaExec>("compileIMDB") {
    group       = "Build"
    description = "Build imdb.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileOODB)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "-L", "$libDir",
            "$imdbMain/x/imdb.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileJsonDB = tasks.register<JavaExec>("compileJsonDB") {
    group       = "Build"
    description = "Build jsondb.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileJson)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "-L", "$libDir",
            "$jsondbMain/x/jsondb.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileWeb = tasks.register<JavaExec>("compileWeb") {
    group       = "Execution"
    description = "Build web.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileNet, compileJson)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "-L", "$libDir",
            "$webMain/x/web.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileBridge = tasks.register<JavaExec>("compileBridge") {
    group         = "Execution"
    description   = "Build javatools_bridge.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileCollections, compileOODB, compileNet, compileWeb)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "-L", "$libDir",
            "$bridgeMain/x/_native.x")
    mainClass.set("org.xvm.tool.Compiler")

    doLast {
        file("$libDir/_native.xtc").
        renameTo(file("$bridgeLib"))
        println("Finished task: compileBridge")
    }
}

val compilePlatform = tasks.register<JavaExec>("compilePlatform") {
    group       = "Execution"
    description = "Build platform.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileWeb)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "-L", "$libDir",
            "$platformMain/x/platform.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileHost = tasks.register<JavaExec>("compileHost") {
    group       = "Build"
    description = "Build host.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileIMDB)
    shouldRunAfter(compileJsonDB)
    shouldRunAfter(compilePlatform)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "-L", "$libDir",
            "$hostMain/x/host.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileHostControl = tasks.register<JavaExec>("compileHostControl") {
    group       = "Execution"
    description = "Build hostControl.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileHost)

    jvmArgs("-Xms1024m", "-Xmx1024m", "-ea")

    classpath(javatoolsJar)
    args("-verbose",
            "-o", "$libDir",
            "-version", "$version",
            "-L", "$coreLib",
            "-L", "$turtleLib",
            "-L", "$libDir",
            "$hostControlMain/x/hostControl.x")
    mainClass.set("org.xvm.tool.Compiler")
}

tasks.register("build") {
    group       = "Build"
    description = "Build the XDK"

    // we assume that the launcher project has been built
    val launcher         = project(":javatools_launcher")
    val macos_launcher   = "${launcher.buildDir}/exe/macos_launcher"
    val windows_launcher = "${launcher.buildDir}/exe/windows_launcher.exe"

    // compile Ecstasy
    val coreSrc = fileTree(ecstasyMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val coreDest = file("$coreLib").lastModified()

    val turtleSrc = fileTree(turtleMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val turtleDest = file("$turtleLib").lastModified()

    if (coreSrc > coreDest || turtleSrc > turtleDest) {
        dependsOn(compileEcstasy)
    } else {
        dependsOn(copyJavatools)
    }

    // compile aggregate.xtclang.org
    val aggregateSrc = fileTree(aggregateMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val aggregateDest = file("$libDir/aggregate.xtc").lastModified()

    if (aggregateSrc > aggregateDest) {
        dependsOn(compileAggregate)
        }

    // compile collections.xtclang.org
    val collectionsSrc = fileTree(collectionsMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val collectionsDest = file("$libDir/collections.xtc").lastModified()

    if (collectionsSrc > collectionsDest) {
        dependsOn(compileCollections)
        }

    // compile crypto.xtclang.org
    val cryptoSrc = fileTree(cryptoMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val cryptoDest = file("$libDir/crypto.xtc").lastModified()

    if (cryptoSrc > cryptoDest) {
        dependsOn(compileCrypto)
        }

    // compile net.xtclang.org
    val netSrc = fileTree(netMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val netDest = file("$libDir/net.xtc").lastModified()

    if (netSrc > netDest) {
        dependsOn(compileNet)
        }

    // compile json.xtclang.org
    val jsonSrc = fileTree(jsonMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val jsonDest = file("$libDir/json.xtc").lastModified()

    if (jsonSrc > jsonDest) {
        dependsOn(compileJson)
        }

    // compile oodb.xtclang.org
    val oodbSrc = fileTree(oodbMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val oodbDest = file("$libDir/oodb.xtc").lastModified()

    if (oodbSrc > oodbDest) {
        dependsOn(compileOODB)
        }

    // compile imdb.xtclang.org
    val imdbSrc = fileTree(imdbMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val imdbDest = file("$libDir/imdb.xtc").lastModified()

    if (imdbSrc > imdbDest) {
        dependsOn(compileIMDB)
        }

    // compile jsondb.xtclang.org
    val jsondbSrc = fileTree(jsondbMain).getFiles().stream().
    mapToLong({f -> f.lastModified()}).max().orElse(0)
    val jsondbDest = file("$libDir/jsondb.xtc").lastModified()

    if (jsondbSrc > jsondbDest) {
        dependsOn(compileJsonDB)
    }

    // compile web.xtclang.org
    val webSrc = fileTree(webMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val webDest = file("$libDir/web.xtc").lastModified()

    if (webSrc > webDest) {
        dependsOn(compileWeb)
        }

    // compile _native.xtclang.org
    val bridgeSrc = fileTree(bridgeMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val bridgeDest = file(bridgeLib).lastModified()

    if (bridgeSrc > bridgeDest) {
        dependsOn(compileBridge, compileNet, compileCrypto)
        }

    // compile platform.xtclang.org
    val platformSrc = fileTree(platformMain).getFiles().stream().
    mapToLong({f -> f.lastModified()}).max().orElse(0)
    val platformDest = file("$libDir/platform.xtc").lastModified()

    if (platformSrc > platformDest) {
        dependsOn(compilePlatform)
    }

    // compile host.xtclang.org
    val hostSrc = fileTree(hostMain).getFiles().stream().
    mapToLong({f -> f.lastModified()}).max().orElse(0)
    val hostDest = file("$libDir/host.xtc").lastModified()

    if (hostSrc > hostDest) {
        dependsOn(compileHost)
    }

    // compile hostControl.xtclang.org
    val hostControlSrc = fileTree(hostControlMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val hostControlDest = file("$libDir/hostControl.xtc").lastModified()

    if (hostControlSrc > hostControlDest) {
        dependsOn(compileHostControl)
        }

    doLast {
        copy {
            from(macos_launcher, windows_launcher)
            into("$buildDir/xdk/bin/")
            }
        println("Finished task: build")
    }

// TODO wiki
// TODO ZIP the resulting xdk directory; e.g. on macOS:
//    `zip -r xdk.zip ./xdk -x *.DS_Store`
}