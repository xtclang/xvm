#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <mach-o/dyld.h>
#include "launcher.h"
#include "os_specific.h"


const char* findLauncherPath()
    {
    char* result = NULL;

    char localBuf[32];
    uint32_t size = 32;
    int err = _NSGetExecutablePath(localBuf, &size);
    switch (err)
        {
        case 0:
            result = allocBuffer(strlen(localBuf));
            strcpy(result, localBuf);
            break;

        case -1:
            result = allocBuffer(size);
            if (_NSGetExecutablePath(result, &size) != 0)
                {
                abortLaunch("failure in _NSGetExecutablePath()");
                }
            break;

        default:
            abortLaunch("failure in initial _NSGetExecutablePath()");
        }

    return result;
    }

const char* extractFile(const char* path)
    {
    char* sep = strrchr(path, '/');
    return sep == NULL ? path : sep+1;
    }

const char* extractDir(const char* path)
    {
    char* sep = strrchr(path, '/');
    if (sep == NULL)
        {
        return NULL;
        }

    int   len    = sep - path + 1;
    char* result = allocBuffer(len);
    memcpy(result, path, len);
    return result;
    }

const char* buildPath(const char* dir, const char* file)
    {
    if (dir == NULL)
        {
        return file;
        }

    int dirLen = strlen(dir);
    if (dirLen <= 0)
        {
        return file;
        }

    if (dir[dirLen-1] == '/')
        {
        --dirLen;
        }

    int   fileLen = strlen(file);
    int   len     = dirLen + 1 + fileLen;
    char* result  = allocBuffer(len);
    memcpy(result, dir, dirLen);
    result[dirLen] = '/';
    memcpy(result + dirLen + 1, file, fileLen);

    return result;
    }

const char* readFile(const char* path)
    {
    FILE *f = fopen(path, "rb");
    if (f == NULL)
        {
        return NULL;
        }

    // determine the size of the file
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    rewind(f);
    if (size < 0 || size > 0x10000)
        {
        fclose(f);
        abortLaunch("file size out of range");
        return NULL;
        }

    char*  result = allocBuffer(size);
    size_t actual = fread(result, 1, size, f);
    fclose(f);
    if (actual != size)
        {
        abortLaunch("error reading file");
        return NULL;
        }

    return result;
    }

void execJava(const char* javaPath, const char* javaOpts,
              const char* jarPath, int argc, const char * argv[])
    {
    // this implementation does not fork()/setsid() because we're not attempting to detach
    // from the terminal that executed the command

    #ifdef DEBUG
    printf("javaPath=%s, javaOpts=%s, jarPath=%s, argc=%d, argv=\n", javaPath, javaOpts, jarPath, argc);
    for (int i = 0; i < argc; ++i)
        {
        printf("[%d] = \"%s\"\n", i, argv[i]);
        }
    #endif // DEBUG

    assert(javaPath != NULL && *javaPath != '\0');
    assert(jarPath  != NULL && *jarPath  != '\0');

    // first, convert the java options into separate parameters
    int          optCount = 0;
    const char** optArgs  = toArgV(javaOpts, &optCount);

    // make the executable file name into the first arg without a path or extension
    assert(argc >= 1);
    argv[0] = removeExtension(extractFile(argv[0]));

    // collect all arguments into one giant list, starting with the call to java
    int allCount = 1          // javaPath
                 + optCount   // javaOpts
                 + 2          // "-jar" jarPath
                 + argc;      // user arguments

    const char** allArgs = malloc((allCount+1) * sizeof(const char*));
    registerGarbage(allArgs);

    memcpy(allArgs+1         , optArgs, optCount * sizeof(const char*));
    memcpy(allArgs+3+optCount, argv   , argc     * sizeof(const char*));
    allArgs[0         ] = javaPath;
    allArgs[optCount+1] = "-jar";
    allArgs[optCount+2] = jarPath;
    allArgs[allCount  ] = NULL;

    #ifdef DEBUG
    printf("resulting %d args:\n", allCount);
    for (int i = 0; i < allCount; ++i)
        {
        printf("[%d] = \"%s\"\n", i, allArgs[i]);
        }
    #endif // DEBUG

    execvp(javaPath, (char* const*) allArgs);
    }
