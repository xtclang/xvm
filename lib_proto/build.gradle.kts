import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.remove

plugins {
    alias(libs.plugins.xtc)
    alias(libs.plugins.google.protobuf)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.xunit)
}

protobuf {
    plugins {
        id("xtc") {
            path = "${rootDir}/build/install/xdk/bin/protoc-gen-xtc"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                remove("java")
            }
            task.plugins {
                id("xtc") {
                    option("packages=google.protobuf=wellknown")
                    option("packages=google.protobuf.compiler=wellknown.compiler")
                }
            }
        }
    }
}
