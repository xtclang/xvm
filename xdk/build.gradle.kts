/*
 * Build files for the XDK.
 */

import java.nio.file.Paths

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
val jsondb        = project(":lib_jsondb")
val web           = project(":lib_web")
val webauth       = project(":lib_webauth")
val xenia         = project(":lib_xenia")

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
val jsondbMain      = "${jsondb.projectDir}/src/main"
val webMain         = "${web.projectDir}/src/main"
val webauthMain     = "${webauth.projectDir}/src/main"
val xeniaMain       = "${xenia.projectDir}/src/main"

val xdkDir          = "$buildDir/xdk"
val binDir          = "$xdkDir/bin"
val libDir          = "$xdkDir/lib"
val coreLib         = "$libDir/ecstasy.xtc"
val javaDir         = "$xdkDir/javatools"
val turtleLib       = "$javaDir/javatools_turtle.xtc"
val bridgeLib       = "$javaDir/javatools_bridge.xtc"

val distDir         = "$buildDir/dist"

val xdkVersion      = rootProject.version
var distName        = xdkVersion
val isCI            = System.getenv("CI")
val buildNum        = System.getenv("BUILD_NUMBER")
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
println("*** XDK distName=${distName}")

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
    group          = "Build"
    description    = "Build ecstasy.xtc and javatools_turtle.xtc modules"

    dependsOn(javatools.tasks["build"])
    dependsOn(copyJavatools)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
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
    group            = "Build"
    description      = "Build aggregate.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileCollections)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
         "-L", "$coreLib",
         "-L", "$turtleLib",
         "$aggregateMain/x/aggregate.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileCollections = tasks.register<JavaExec>("compileCollections") {
    group              = "Build"
    description        = "Build collections.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileEcstasy)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
         "-L", "$coreLib",
         "-L", "$turtleLib",
         "$collectionsMain/x/collections.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileCrypto = tasks.register<JavaExec>("compileCrypto") {
    group         = "Build"
    description   = "Build crypto.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileEcstasy)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
         "-L", "$coreLib",
         "-L", "$turtleLib",
         "$cryptoMain/x/crypto.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileNet  = tasks.register<JavaExec>("compileNet") {
    group       = "Build"
    description = "Build net.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileCrypto)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
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

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
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

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
         "-L", "$coreLib",
         "-L", "$turtleLib",
         "$oodbMain/x/oodb.x")
   mainClass.set("org.xvm.tool.Compiler")
}

val compileJsonDB = tasks.register<JavaExec>("compileJsonDB") {
    group         = "Build"
    description   = "Build jsondb.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileJson, compileOODB)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
         "-L", "$coreLib",
         "-L", "$turtleLib",
         "-L", "$libDir",
         "$jsondbMain/x/jsondb.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileWeb  = tasks.register<JavaExec>("compileWeb") {
    group       = "Execution"
    description = "Build web.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileNet, compileJson)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
         "-L", "$coreLib",
         "-L", "$turtleLib",
         "-L", "$libDir",
         "$webMain/x/web.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileWebauth = tasks.register<JavaExec>("compileWebauth") {
    group          = "Execution"
    description    = "Build webauth.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileOODB, compileWeb)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
         "-L", "$coreLib",
         "-L", "$turtleLib",
         "-L", "$libDir",
         "$webauthMain/x/webauth.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileXenia = tasks.register<JavaExec>("compileXenia") {
    group        = "Execution"
    description  = "Build xenia.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileWeb, compileWebauth)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
         "-L", "$coreLib",
         "-L", "$turtleLib",
         "-L", "$libDir",
         "$xeniaMain/x/xenia.x")
    mainClass.set("org.xvm.tool.Compiler")
}

val compileBridge = tasks.register<JavaExec>("compileBridge") {
    group         = "Execution"
    description   = "Build javatools_bridge.xtc module"

    dependsOn(javatools.tasks["build"])

    shouldRunAfter(compileCollections, compileOODB, compileNet, compileWeb, compileXenia)

    classpath(javatoolsJar)
    args("-o", "$libDir",
         "-stamp", "$xdkVersion",
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

    // compile webauth.xtclang.org
    val webauthSrc = fileTree(webauthMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val webauthDest = file("$libDir/webauth.xtc").lastModified()

    if (webauthSrc > webauthDest) {
        dependsOn(compileWebauth)
        }

    // compile xenia.xtclang.org
    val xeniaSrc = fileTree(xeniaMain).getFiles().stream().
            mapToLong({f -> f.lastModified()}).max().orElse(0)
    val xeniaDest = file("$libDir/xenia.xtc").lastModified()

    if (xeniaSrc > xeniaDest) {
        dependsOn(compileXenia)
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

val prepareDirs = tasks.register("prepareDirs") {
    mustRunAfter("clean")

    doLast {
        mkdir("$distDir")
    }
}

tasks.register("dist-local") {
    group       = "Distribution"
    description = "Copy the xdk to the local homebrew cellar"

    dependsOn(build)

    doLast {
        // getting the homebrew xdl location using "readlink -f `which xec`" command
        val output = java.io.ByteArrayOutputStream()

        project.exec {
            commandLine("which", "xec")
            standardOutput = output
            setIgnoreExitValue(true)
        }

        val xecLink = output.toString().trim()
        if (xecLink.length > 0) {
            val xecFile    = Paths.get(xecLink).toRealPath()
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
}

val distTGZ = tasks.register<Tar>("distTGZ") {
    group       = "Distribution"
    description = "Create the XDK .tar.gz file"

    dependsOn(build)
    dependsOn(prepareDirs)

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
    dependsOn(prepareDirs)

    archiveFileName.set("xdk-${distName}.zip")
    destinationDirectory.set(file("$distDir/"))
    from("$buildDir/") {
        include("xdk/**")
    }
}

val distEXE = tasks.register("distEXE") {
    group = "Distribution"
    description = "Create the XDK .exe file (Windows installer)"

    dependsOn(build)
    dependsOn(prepareDirs)

    doLast {
        val output = java.io.ByteArrayOutputStream()
        project.exec {
            commandLine("which", "makensis")
            standardOutput = output
            setIgnoreExitValue(true)
        }
        if (output.toString().trim().length > 0) {
            // notes:
            // - requires NSIS to be installed (e.g. "sudo apt install nsis" works on Debian/Ubuntu)
            // - requires the "makensis" command to be in the path
            // - requires the EnVar plugin to be installed (i.e. unzipped) into NSIS

            val src  = file("src/main/nsi/xdkinstall.nsi")
            val dest = "${distDir}/xdk-${distName}.exe"
            val ico  = "${launcherMain}/c/x.ico"

            project.exec {
                environment("NSIS_SRC", "${xdkDir}")
                environment("NSIS_ICO", "${ico}")
                environment("NSIS_VER", "${distName}")
                environment("NSIS_OUT", "${dest}")
                commandLine("makensis", "${src}", "-NOCD")
            }
        }
        else {
            println("*** Failure building \"distEXE\": Missing \"makensis\" command")
        }
    }
}

tasks.register("dist") {
    group = "Distribution"
    description = "Create the various XDK distributions"

    dependsOn(prepareDirs)
    dependsOn(distTGZ)
    dependsOn(distZIP)
    dependsOn(distEXE)
}