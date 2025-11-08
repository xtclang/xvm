package org.xtclang.plugin;

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

    // XTC Runtime constants:
    public static final String XTC_EXTENSION_NAME_RUNTIME = "xtcRun";
    public static final String XTC_DEFAULT_RUN_METHOD_NAME_PREFIX = "run";
    public static final String XTC_RUNNER_CLASS_NAME = "org.xvm.tool.Runner";

    // XDK Distribution constants:
    public static final String XDK_CONFIG_NAME_INCOMING = "xdk";
    public static final String XDK_CONFIG_NAME_INCOMING_ZIP = "xdkDistribution";
    public static final String XDK_CONFIG_NAME_CONTENTS = "xdkContents";
    public static final String XDK_LIBRARY_ELEMENT_TYPE_XDK_CONTENTS = "xdk-contents";
    public static final String XDK_LIBRARY_ELEMENT_TYPE = "xdk-distribution-archive";
    public static final String XDK_EXTRACT_TASK_NAME = "extractXdk";
    public static final String XDK_VERSION_TASK_NAME = "xtcVersion";
    public static final String XDK_VERSION_GROUP_NAME = "version";

    // Library (mostly Java tools) constants:
    public static final String XDK_JAVATOOLS_ARTIFACT_ID = "javatools";
    public static final String XDK_JAVATOOLS_ARTIFACT_SUFFIX = "jar";
    public static final String XDK_CONFIG_NAME_JAVATOOLS_INCOMING = "xdkJavaTools";
    public static final String XDK_CONFIG_NAME_JAVATOOLS_OUTGOING = XDK_CONFIG_NAME_JAVATOOLS_INCOMING + "Provider";
    public static final String XDK_JAVATOOLS_NAME_MANIFEST = "META-INF/MANIFEST.MF";
    public static final String XDK_JAVATOOLS_NAME_JAR = XDK_JAVATOOLS_ARTIFACT_ID + '.' + XDK_JAVATOOLS_ARTIFACT_SUFFIX;

    public static final String PLUGIN_BUILD_INFO_FILENAME = "plugin-build-info.properties";
    public static final String PLUGIN_BUILD_INFO_RESOURCE_PATH = "/org/xtclang/build/internal/" + PLUGIN_BUILD_INFO_FILENAME;

    // Config artifacts from the XDK build; this is only resolved when we are using the plugin to build the XDK itself,
    // of which it is a part, the XdkDistribution supplied the artifact name for the javatools the plugin needs.
    // If we are applying the plugin to an external project (all other use cases), then we depend on extractXdk to
    // unpack the zipped xtc-plugin artifact to a build system location, and use that javatools.jar as classpath.
    public static final String XDK_CONFIG_NAME_ARTIFACT_JAVATOOLS_JAR = "javatools-jar";

    // XTC Magic Number, for future verification of XTC module binaries, and for parts of language server support.
    public static final long XTC_MAGIC = 0xEC57_A5EEL;

    // Project property names
    public static final String PROPERTY_VERBOSE_LOGGING_OVERRIDE = "xtcPluginOverrideVerboseLogging";
    public static final String PROPERTY_LAUNCHER_FORK = "org.xtclang.plugin.launcher.fork";

    public static final String UNSPECIFIED = Project.DEFAULT_VERSION;

    private XtcPluginConstants() {
    }
}
