package org.xvm.plugin;

import org.gradle.api.Project;

import java.io.File;
import java.util.Collections;
import java.util.Set;

final class Constants {
    static final String XDK_CONFIG_NAME_CONTENTS = "xdkContents";
    static final String XDK_LIBRARY_ELEMENT_TYPE_XDK_CONTENTS = "xdk-contents";
    static final String XDK_LIBRARY_ELEMENT_TYPE = "xdk-distribution-archive";
    static final String XDK_VERSION_PATH = "VERSION";

    static final String XTC_COMPONENT_NAME = "xtcComponent";
    static final String XTC_LANGUAGE_NAME = "xtc";
    static final String XTC_MODULE_FILE_EXTENSION = XTC_LANGUAGE_NAME;
    static final String XTC_SOURCE_FILE_EXTENSION = "x";
    static final String XTC_SOURCE_SET_DIRECTORY_ROOT_NAME = "x";
    static final String XTC_CONFIG_NAME_INCOMING = "xtcModule";
    static final String XTC_CONFIG_NAME_OUTGOING = XTC_CONFIG_NAME_INCOMING + "Provider";
    static final String XTC_CONFIG_NAME_MODULE_DEPENDENCY = "xtcModuleDeps";
    static final String XTC_CONFIG_NAME_INCOMING_TEST = XTC_CONFIG_NAME_INCOMING + "Test";
    static final String XTC_CONFIG_NAME_OUTGOING_TEST = XTC_CONFIG_NAME_OUTGOING + "Test";
    static final String XTC_COMPONENT_VARIANT_COMPILE = "compile";
    static final String XTC_COMPONENT_VARIANT_RUNTIME = "runtime";
    static final String XTC_VERSION_GROUP_NAME = "version";
    static final String XTC_VERSIONFILE_TASK_NAME = "versionFile";
    static final String XTC_VERSION_TASK_NAME = XTC_VERSION_GROUP_NAME;
    static final String XTC_DEFAULT_RUN_METHOD_NAME_PREFIX = "run";
    static final String XTC_EXTENSION_NAME_COMPILER = "xtcCompile";
    static final String XTC_EXTENSION_NAME_RUNTIME = "xtcRun";

    static final long XTC_MAGIC = 0xEC57_A5EEL;

    static final String JAVATOOLS_ARTIFACT_ID = "javatools";
    static final String JAVATOOLS_FILENAME = JAVATOOLS_ARTIFACT_ID + ".jar";
    static final String XTC_CONFIG_NAME_JAVATOOLS_INCOMING = "xtcJavaTools";
    static final String XTC_CONFIG_NAME_JAVATOOLS_OUTGOING = XTC_CONFIG_NAME_JAVATOOLS_INCOMING + "Provider";

    static final String VERSION_CATALOG_XTC_VERSION = "xdk";
    static final String VERSION_CATALOG_XTC_GROUP = "group";

    static final Set<File> EMPTY_FILE_SET = Collections.emptySet();

    static final String UNSPECIFIED = Project.DEFAULT_VERSION;

    static final String JAR_MANIFEST_PATH = "META-INF/MANIFEST.MF";
}
