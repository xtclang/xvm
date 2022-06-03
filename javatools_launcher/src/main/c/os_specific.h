#pragma once

#define DEFAULT_EXEC "java"
#define DEFAULT_OPTS "-Xms256m -Xmx1024m -ea"
#define PROTO_JAR    "javatools.jar"
#define MACK_LIB     "javatools_turtle.xtc"
#define PROTO_LIB    "javatools_bridge.xtc"

#ifdef windowsLauncher
#define FILE_SEPERATOR '\\'
#define PROTO_DIR      "..\\javatools\\"
#define LIB_DIR        "..\\lib\\"
#else
#define FILE_SEPERATOR '/'
#define PROTO_DIR      "../javatools/"
#define LIB_DIR        "../lib/"
#endif

/**
 * Determine the path of this executable.
 *
 * @return the path of the launcher
 */
extern const char* findLauncherPath();

/**
 * If the file is a link, follow the link until a real file is found.
 *
 * @param path  the path to a file that may be a link
 *
 * @return the file at the end of the linked list of links
 */
extern const char* resolveLinks(const char* path);

/**
 * Execute the JVM against the specified JAR.
 *
 * @param javaPath  the path to use to execute the JVM (e.g. "java")
 * @param javaOpts  the JVM options (e.g. "-Xmx=512m")
 * @param jarPath   the directory path containing javatools.jar and javatools_bridge.xtc
 * @param libPath   the directory path containing Ecstasy.xtc and other modules
 * @param argc      the number of arguments to pass along
 * @param argv      the arguments to pass along
 */
extern void execJava(const char* javaPath,
                     const char* javaOpts,
                     const char* jarPath,
                     const char* libPath,
                     int         argc,
                     const char* argv[]);