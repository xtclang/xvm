#pragma once

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
 * @param cmd   a string in the form that one would expect a command line statement to take
 * @param argc  a optional pointer to an "argc" value that will hold the number of arguments
 *              returned in "argv"
 *
 * @return an array in the "argv" format
 */
extern const char** toArgV(const char* cmd, int* argc);

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
 * @param file  a file name
 *
 * @return the file name without an extension
 */
extern const char* removeExtension(const char* file);

/**
 * Test for the existence of the file or directory at the specified location.
 *
 * @param path  the path of the file or directory
 *
 * @return true iff a file or directory at the specified path exists
 */
int testExists(const char* path);

/**
 * Read the file at the specified location.
 *
 * @param path  the path of the file to read
 *
 * @return the file contents as a string, with the caller being responsible to free() the returned
 *         pointer
 */
extern const char* readFile(const char* path);

