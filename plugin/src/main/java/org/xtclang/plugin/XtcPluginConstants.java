package org.xtclang.plugin;

import java.io.File;

import java.util.Collections;
import java.util.Set;

import org.gradle.api.Project;

public final class XtcPluginConstants {
    // XTC Language and dependency constants:
    public static final String XTC_COMPONENT_NAME = "xtcComponent";
    public static final String XTC_LANGUAGE_NAME = "xtc";
    public static final String XTC_MODULE_FILE_EXTENSION = XTC_LANGUAGE_NAME;
    public static final String XTC_SOURCE_FILE_EXTENSION = "x";
    public static final String XTC_SOURCE_SET_DIRECTORY_ROOT_NAME = "x";
    public static final String XTC_CONFIG_NAME_INCOMING = "xtcModule";
    public static final String XTC_CONFIG_NAME_OUTGOING = XTC_CONFIG_NAME_INCOMING + "Provider";
    public static final String XTC_CONFIG_NAME_MODULE_DEPENDENCY = "xtcModuleDeps";

    // XTC Compile time constants
    public static final String XTC_EXTENSION_NAME_COMPILER = "xtcCompile";
    public static final String XTC_COMPILER_CLASS_NAME = "org.xvm.tool.Compiler";
    public static final String XTC_COMPILER_LAUNCHER_NAME = "xcc";
    @SuppressWarnings("unused") // TODO: This will be added to facilitate publication of single XTC project artifacts.
    public static final String XTC_COMPONENT_VARIANT_COMPILE = "compile";
    public static final String XTC_COMPILE_MAIN_TASK_NAME = "compileXtc";

    // XTC Runtime constants:
    public static final String XTC_EXTENSION_NAME_RUNTIME = "xtcRun";
    public static final String XTC_DEFAULT_RUN_METHOD_NAME_PREFIX = "run";
    public static final String XTC_RUNNER_CLASS_NAME = "org.xvm.tool.Runner";
    public static final String XTC_RUNNER_LAUNCHER_NAME = "xec";
    @SuppressWarnings("unused") // TODO: This will be added to facilitate publication of single XTC project artifacts.
    public static final String XTC_COMPONENT_VARIANT_RUNTIME = "runtime";

    // XDK Distribution constants:
    public static final String XDK_CONFIG_NAME_INCOMING = "xdk";
    public static final String XDK_CONFIG_NAME_INCOMING_ZIP = "xdkDistribution";
    public static final String XDK_CONFIG_NAME_CONTENTS = "xdkContents";
    public static final String XDK_LIBRARY_ELEMENT_TYPE_XDK_CONTENTS = "xdk-contents";
    public static final String XDK_LIBRARY_ELEMENT_TYPE = "xdk-distribution-archive";
    public static final String XDK_EXTRACT_TASK_NAME = "extractXdk";
    public static final String XDK_VERSION_FILE_TASK_NAME = "xtcVersionFile";
    public static final String XDK_VERSION_TASK_NAME = "xtcVersion";
    public static final String XDK_VERSION_GROUP_NAME = "version";
    public static final String XDK_VERSION_PATH = "VERSION";

    // Library (mostly Java tools) constants:
    public static final String XDK_JAVATOOLS_ARTIFACT_ID = "javatools";
    public static final String XDK_JAVATOOLS_ARTIFACT_SUFFIX = "jar";
    public static final String XDK_CONFIG_NAME_JAVATOOLS_INCOMING = "xdkJavaTools";
    public static final String XDK_CONFIG_NAME_JAVATOOLS_OUTGOING = XDK_CONFIG_NAME_JAVATOOLS_INCOMING + "Provider";

    // Config artifacts from the XDK build:
    public static final String XDK_CONFIG_NAME_ARTIFACT_JAVATOOLS_FATJAR = "javatools-fatjar";

    // Debugging (for example, adding significant events to output without increasing the log level)
    public static final String XTC_PLUGIN_VERBOSE_PROPERTY = "ORG_XTCLANG_PLUGIN_VERBOSE";

    // Default "empty" values for collections and Gradle API classes.
    public static final Set<File> EMPTY_FILE_COLLECTION = Collections.emptySet();
    public static final String UNSPECIFIED = Project.DEFAULT_VERSION;

    // JavaTools (launcher native code)
    public static final String JAR_MANIFEST_PATH = "META-INF/MANIFEST.MF";
    public static final String JAVATOOLS_JAR_NAME = "javatools.jar";

    // XTC Magic Number, for future verification of XTC module binaries, and for parts of language server support.
    @SuppressWarnings("unused")
    public static final long XTC_MAGIC = 0xEC57_A5EEL;

    private XtcPluginConstants() {
    }
}
