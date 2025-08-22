#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include "launcher.h"
#include "os_specific.h"


// shared code for Linux and macos implementations

const char* getXdkHome() {
    return (const char*) getenv(XDK_HOME);
}

const char* resolveLinks(const char* path) {
    char ach[PATH_MAX];
    if (!realpath(path, ach)) {
        abortLaunch("Unresolvable link");
    }

    if (strcmp(path, ach) == 0) {
        return path;
    }

    char* result = allocBuffer(strlen(ach)+1);
    strcpy(result, ach);
    return result;
}

int execJava(const char* javaPath,
             const char* javaOpts,
             const char* jarPath,
             const char* libPath,
             int         argc,
             const char* argv[]) {
    // this implementation does not fork()/setsid() because we're not attempting to detach
    // from the terminal that executed the command

    #ifdef DEBUG
    printf("javaPath=%s, javaOpts=%s, jarPath=%s, libPath=%s, argc=%d, argv=\n",
            javaPath,    javaOpts,    jarPath,    libPath,    argc);
    for (int i = 0; i < argc; ++i) {
        printf("[%d] = \"%s\"\n", i, argv[i]);
    }
    #endif // DEBUG

    assert(javaPath != NULL && *javaPath != '\0');
    assert(jarPath  != NULL && *jarPath  != '\0');
    assert(libPath  != NULL && *libPath  != '\0');
    if (javaOpts == NULL) {
        javaOpts = "";
    }

    // the native ecstasy library is located in the same location as the prototype JAR
    const char* jarFile  = buildPath(jarPath, PROTO_JAR);
    const char* mackFile = buildPath(jarPath, MACK_LIB);
    const char* libFile  = buildPath(jarPath, PROTO_LIB);

    // first, convert the java options into separate parameters
    int          optCount = 0;
    const char** optArgs  = toArgV(javaOpts, &optCount);

    // make the executable file name into the first arg without a path or extension
    assert(argc >= 1);
    const char* tool = removeExtension(extractFile(argv[0]));
    --argc;
    ++argv;

    // collect all arguments into one giant list, starting with the call to java
    int allCount = 1          // javaPath
                 + optCount   // javaOpts
                 + 9          // "-jar" jarFile tool "-L" libPath "-L" mackFile "-L" libFile
                 + argc;      // user arguments

    const char** allArgs = malloc((allCount+1) * sizeof(const char*));
    registerGarbage(allArgs);

    int i = 0;
    allArgs[i++] = javaPath;
    memcpy(allArgs+i, optArgs, optCount * sizeof(const char*)); i += optCount;
    allArgs[i++] = "-jar";
    allArgs[i++] = jarFile;
    allArgs[i++] = tool;
    allArgs[i++] = "-L";
    allArgs[i++] = libPath;
    allArgs[i++] = "-L";
    allArgs[i++] = mackFile;
    allArgs[i++] = "-L";
    allArgs[i++] = libFile;
    memcpy(allArgs+i, argv, argc * sizeof(const char*)); i += argc;
    allArgs[i  ] = NULL;
    assert(i == allCount);

    #ifdef DEBUG
    printf("resulting %d args:\n", allCount);
    for (i = 0; i < allCount; ++i) {
        printf("[%d] = \"%s\"\n", i, allArgs[i]);
    }
    #endif // DEBUG

    // this function only returns if the process creation fails; if it succeeds, this process is
    // replaced entirely by the process that is being created, i.e. this function does not return;
    // see: https://linux.die.net/man/3/execvp
    execvp(javaPath, (char* const*) allArgs);
    abortLaunch(NULL);      // errno will already be set by the OS if this code is reachable
    return EXIT_FAILURE;    // this code is unreachable
}