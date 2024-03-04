#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <windows.h>
#include "launcher.h"
#include "os_specific.h"


const char* findLauncherPath() {
    char* result = NULL;

    const DWORD size = 260;
    char localBuf[size];
    DWORD len = GetModuleFileName(NULL, localBuf, size);
    if (len <= 0 || len >= size) {
        abortLaunch("failure in GetModuleFileName()");
    }

    result = allocBuffer(len);
    strncpy(result, localBuf, len);
    return result;
}

const char* getXdkHome() {
// note: gcc+mingw can't figure out what getenv_s is, even with __STDC_WANT_LIB_EXT1__=1
//
//    char*  buf = NULL;
//    size_t len = 0;
//    getenv_s(&len, buf, len, XDK_HOME);         // get the length
//    if (len > 0) {
//        buf = allocBuffer(len);
//        getenv_s(&len, buf, len, XDK_HOME);     // get the string
//    }
//
//    return (const char*) buf;
    return getenv(XDK_HOME);
}

const char* resolveLinks(const char* path) {
    return path;
}

const char* escapePath(const char* path) {
    if (*path == '\0' || *path == '\"' || !strchr(path, ' ')) {
        return path;
    }	      

    int   len    = strlen(path);
    char* result = allocBuffer(len+3);
    result[0] = '\"';
    strcpy(result+1, path);

    // disallow path ending in slash
    while (len >= 0 && result[len] == '\\') {
        --len;
    }

    result[len+1] = '\"';
    result[len+2] = '\0';
    return result;
}

void execJava(const char* javaPath,
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

    // make the executable file name into the first arg without a path or extension
    assert(argc >= 1);
    const char* tool = removeExtension(extractFile(argv[0]));
    --argc;
    ++argv;

    // handle windows weirdness with paths
    javaPath = escapePath(javaPath);
    jarFile  = escapePath(jarFile);
    libPath  = escapePath(libPath);
    mackFile = escapePath(mackFile);
    libFile  = escapePath(libFile);

    #ifdef DEBUG
    printf("after escape: javaPath=%s, jarFile=%s, tool=%s, libPath=%s, mackFile=%s, libFile=%s\n",
                          javaPath,    jarFile,    tool,    libPath,    mackFile,    libFile);
    for (int i = 0; i < argc; ++i) {
        printf("[%d] = \"%s\"\n", i, argv[i]);
    }
    #endif // DEBUG

    // collect all arguments into one giant string, starting with the call to java
    int len = strlen(javaPath)
            + strlen(" ")
            + strlen(javaOpts)
            + strlen(" -jar ")
            + strlen(jarFile)
            + strlen(" ")
            + strlen(tool)
            + strlen(" -L ")
            + strlen(libPath)
            + strlen(" -L ")
            + strlen(mackFile)
            + strlen(" -L ")
            + strlen(libFile);
    for (int i = 0; i < argc; ++i) {
        len += 1 + strlen(argv[i]);
    }

    char* cmd = malloc(len);
    registerGarbage(cmd);

    strcpy(cmd, javaPath);
    strcat(cmd, " ");
    strcat(cmd, javaOpts);
    strcat(cmd, " -jar ");
    strcat(cmd, jarFile);
    strcat(cmd, " ");
    strcat(cmd, tool);
    strcat(cmd, " -L ");
    strcat(cmd, libPath);
    strcat(cmd, " -L ");
    strcat(cmd, mackFile);
    strcat(cmd, " -L ");
    strcat(cmd, libFile);
    for (int i = 0; i < argc; ++i) {
        strcat(cmd, " ");
        strcat(cmd, argv[i]);
    }

    #ifdef DEBUG
    printf("resulting command: %s\n", cmd);
    #endif // DEBUG

    // prepare process
    STARTUPINFO si;
    PROCESS_INFORMATION pi;
    memset(&si, 0, sizeof(STARTUPINFO));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_FORCEONFEEDBACK;
    memset(&pi, 0, sizeof(PROCESS_INFORMATION));

    // execute the command line
    if (!CreateProcess(NULL, cmd, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
        abortLaunch(cmd);
    }

    WaitForSingleObject(pi.hProcess, INFINITE);

    // the handles need to be released; this isn't killing the process
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
}
