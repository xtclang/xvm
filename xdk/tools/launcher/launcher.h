#pragma once

#define DEFAULT_EXEC "java"
#define DEFAULT_OPTS "-Xms256m -Xmx1024m -ea"

#ifdef windowsLauncher
#define DEFAULT_JAR  "..\\prototype\\xvm.jar"
#else
#define DEFAULT_JAR  "../prototype/xvm.jar"
#endif

#define MAX_GARBAGE 64

/**
 * Abort the launcher and print an error message.
 *
 * @param message  an optional error message
 */
extern void abortLaunch(const char* message);

/**
 * Create a buffer of the specified size (plus one for the null terminator) and register the buffer
 * as future garbage.
 *
 * @param size  the size of the contents of the buffer
 *
 * @return a pointer to a dynamically allocated buffer of size `size+1` initialized to all '\0's
 */
extern char* allocBuffer(int size);

/**
 * @param buffer  a pointer to eventually free
 */
extern void registerGarbage(void* buffer);

/**
 * @param file  a file name
 *
 * @return the file name without an extension
 */
extern const char* removeExtension(const char* file);

/**
 * @param cmd   a string in the form that one would expect a command line statement to take
 * @param argc  a optional pointer to an "argc" value that will hold the number of arguments
 *              returned in "argv"
 *
 * @return an array in the "argv" format
 */
extern const char** toArgV(const char* cmd, int* argc);
