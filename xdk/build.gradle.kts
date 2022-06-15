/*
 * Build files for the XDK.
 */

val javatools     = project(":javatools")
val launcher      = project(":javatools_launcher")
val turtle        = project(":javatools_turtle")
val bridge        = project(":javatools_bridge")
val ecstasy       = project(":lib_ecstasy")
val aggregate     = project(":lib_aggregate")
val collections   = project(":lib_collections")
val crypto        = project(":lib_crypto")
val net           = project(":lib_net")
val json          = project(":lib_json")
val oodb          = project(":lib_oodb")
val imdb          = project(":lib_imdb")
val jsondb        = project(":lib_jsondb")
val web           = project(":lib_web")

val ecstasyMain     = "${ecstasy.projectDir}/src/main"
val turtleMain      = "${turtle.projectDir}/src/main"
val bridgeMain      = "${bridge.projectDir}/src/main"
val javatoolsJar    = "${javatools.buildDir}/libs/javatools.jar"
val launcherMain    = "${launcher.projectDir}/src/main"
val aggregateMain   = "${aggregate.projectDir}/src/main"
val collectionsMain = "${collections.projectDir}/src/main"
val cryptoMain      = "${crypto.projectDir}/src/main"
val netMain         = "${net.projectDir}/src/main"
val jsonMain        = "${json.projectDir}/src/main"
val oodbMain        = "${oodb.projectDir}/src/main"
val imdbMain        = "${imdb.projectDir}/src/main"
val jsondbMain      = "${jsondb.projectDir}/src/main"
val webMain         = "${web.projectDir}/src/main"

val xdkDir          = "$buildDir/xdk"
val binDir          = "$xdkDir/bin"
val libDir          = "$xdkDir/lib"
val coreLib         = "$libDir/ecstasy.xtc"
val javaDir         = "$xdkDir/javatools"
val turtleLib       = "$javaDir/javatools_turtle.xtc"
val bridgeLib       = "$javaDir/javatools_bridge.xtc"

val distDir         = "$buildDir/dist"

val xdkVersion      = rootProject.version

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
    into("$javaDir/")

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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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
    args("-o", "$libDir",
         "-version", "$xdkVersion",
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

val build = tasks.register("build") {
    group       = "Build"
    description = "Build the XDK"

    // we assume that the launcher project has been built
    val launcher         = project(":javatools_launcher")
    val linux_launcher   = "${launcher.buildDir}/exe/linux_launcher"
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

    doLast {
        copy {
            from(linux_launcher, macos_launcher, windows_launcher)
            into(binDir)
        }
        println("Finished task: build")
    }
}

// TODO wiki

tasks.register<Tar>("dist-local") {
    group       = "Distribution"
    description = "Copy the xdk to the local homebrew cellar"

    dependsOn(build)

    // getting the homebrew xdl location using "readlink -f `which xec`" command
    val output = java.io.ByteArrayOutputStream()

    project.exec {
        commandLine("which", "xec")
        standardOutput = output
        setIgnoreExitValue(true)
    }

    val xecLink = output.toString().trim()
    if (xecLink.length > 0) {
        output.reset()
        project.exec {
            commandLine("readlink", "-f", xecLink)
            standardOutput = output
        }

        val xecFile    = output.toString().trim();
        val libexecDir = file("$xecFile/../..")
        var updated    = false;

        val srcBin = fileTree("$binDir").getFiles().stream().
                mapToLong({f -> f.lastModified()}).max().orElse(0)
        val dstBin = fileTree("$libexecDir/bin").getFiles().stream().
                mapToLong({f -> f.lastModified()}).max().orElse(0)
        if (srcBin > dstBin) {
            copy {
                from("$binDir/")
                into("$libexecDir/bin")
            }
            updated = true;
        }

        val srcLib = fileTree("$libDir/").getFiles().stream().
                mapToLong({f -> f.lastModified()}).max().orElse(0)
        val dstLib = fileTree("$libexecDir/lib").getFiles().stream().
                mapToLong({f -> f.lastModified()}).max().orElse(0)
        if (srcLib > dstLib) {
            copy {
                from("$libDir/")
                into("$libexecDir/lib")
            }
            updated = true;
        }

        val srcJts = fileTree("$javaDir/").getFiles().stream().
                mapToLong({f -> f.lastModified()}).max().orElse(0)
        val dstJts = fileTree("$libexecDir/javatools").getFiles().stream().
                mapToLong({f -> f.lastModified()}).max().orElse(0)
        if (srcJts > dstJts) {
            copy {
                from("$javaDir/")
                into("$libexecDir/javatools")
            }
            updated = true;
        }

        if (updated) {
            println("Updated local homebrew directory $libexecDir")
        }
    }
    else {
        println("Missing local homebrew installation; run \"brew install xdk\" command first")
    }
}

val distTGZ = tasks.register<Tar>("distTGZ") {
    group       = "Distribution"
    description = "Create the XDK .tar.gz file"

    dependsOn(build)

    var distName = xdkVersion
    val isCI     = System.getenv("CI")
    val buildNum = System.getenv("BUILD_NUMBER")
    if (isCI != null && isCI != "0" && isCI != "false" && buildNum != null) {
        distName = "${distName}ci${buildNum}"

        val output = java.io.ByteArrayOutputStream()
        project.exec {
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = output
            setIgnoreExitValue(true)
        }
        val changeId = output.toString().trim()
        if (changeId.length > 0) {
            distName = "${distName}+${changeId}"
        }
    }

    archiveFileName.set("xdk-${distName}.tar.gz")
    destinationDirectory.set(file("$distDir/"))
    compression = Compression.GZIP
    from("$buildDir/") {
        include("xdk/**")
    }
}

val distZIP = tasks.register<Zip>("distZIP") {
    group       = "Distribution"
    description = "Create the XDK .zip file"

    dependsOn(build)

    var distName = xdkVersion
    val isCI     = System.getenv("CI")
    val buildNum = System.getenv("BUILD_NUMBER")
    if (isCI != null && isCI != "0" && isCI != "false" && buildNum != null) {
        distName = "${distName}ci${buildNum}"

        val output = java.io.ByteArrayOutputStream()
        project.exec {
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = output
            setIgnoreExitValue(true)
        }
        val changeId = output.toString().trim()
        if (changeId.length > 0) {
            distName = "${distName}+${changeId}"
        }
    }

    archiveFileName.set("xdk-${distName}.zip")
    destinationDirectory.set(file("$distDir/"))
    from("$buildDir/") {
        include("xdk/**")
    }
}

val distMSI = tasks.register<Tar>("distMSI") {
    group = "Distribution"
    description = "Create the XDK .msi file (Windows installer)"

    dependsOn(build)

    // notes:
    // - requires NSIS to be installed (e.g. "sudo apt install nsis" works on Debian/Ubuntu)
    // - requires the "makensis" command to be in the path
    // - requires the EnVar plugin to be installed (i.e. unzipped) into NSIS

    val src  = file("src/main/nsi/xdkinstall.nsi")
    val dest = "${distDir}/xdkinstall.msi"
    val ico  = "${launcherMain}/c/x.ico"

    project.exec {
        commandLine("makensis", "${src}", "-NOCD", "-DSRC=${xdkDir}", "-DVER=${xdkVersion}",
                "-DMUI_ICON=${ico}", "-DOutFile=${dest}")
    }
}

tasks.register<Tar>("dist") {
    group = "Distribution"
    description = "Create the various XDK distributions"

    dependsOn(distTGZ)
    dependsOn(distZIP)
    dependsOn(distMSI)
}
