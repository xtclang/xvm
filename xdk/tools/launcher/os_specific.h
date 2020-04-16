#pragma once

/**
 * Determine the path of this executable.
 *
 * @return the path of the launcher
 */
extern const char* findLauncherPath();

/**
 * Obtain the file name from the specified file path.
 *
 * @param path  the path to extract the file name from
 *
 * @return the name of the file
 */
extern const char* extractFile(const char* path);

/**
 * Obtain the directory portion from the specified file path.
 *
 * @param path  the path to extract the directory from
 *
 * @return the directory path
 */
extern const char* extractDir(const char* path);

/**
 * Combine the specified directory and file into a file path.
 *
 * @param dir   the directory path string
 * @param file  the file name
 *
 * @return the path composed of the specified directory and file name
 */
extern const char* buildPath(const char* dir, const char* file);

/**
 * Read the file at the specified location.
 *
 * @param path  the path of the file to read
 *
 * @return the file contents as a string, with the caller being responsible to free() the returned
 *         pointer
 */
extern const char* readFile(const char* path);

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