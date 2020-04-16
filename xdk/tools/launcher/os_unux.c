#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include "launcher.h"
#include "os_specific.h"


// shared code for Linux and macos implementations


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
