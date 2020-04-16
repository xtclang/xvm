#pragma once

#define DEFAULT_EXEC "java"
#define DEFAULT_OPTS "-Xms256m -Xmx1024m -ea"

#ifdef windowsLauncher
#define DEFAULT_JAR    "..\\prototype\\xvm.jar"
#define FILE_SEPERATOR '\\'
#else
#define DEFAULT_JAR  "../prototype/xvm.jar"
#define FILE_SEPERATOR '/'
#endif

/**
 * Determine the path of this executable.
 *
 * @return the path of the launcher
 */
extern const char* findLauncherPath();

/**
 * Execute the JVM against the specified JAR.
 *
 * @param javaPath  the path to use to execute the JVM (e.g. "java")
 * @param javaOpts  the JVM options (e.g. "-Xmx=512m")
 * @param jarPath   the path to the JAR to execute
 * @param argc      the number of arguments to pass along
 * @param argv      the arguments to pass along
 */
extern void execJava(const char* javaPath, const char* javaOpts,
                     const char* jarPath, int argc, const char * argv[]);