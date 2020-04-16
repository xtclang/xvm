#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <windows.h>
#include "launcher.h"
#include "os_specific.h"


const char* findLauncherPath()
    {
    char* result = NULL;

    const DWORD size = 260;
    char localBuf[size];
    DWORD len = GetModuleFileName(NULL, localBuf, size);
    if (len <= 0 || len >= size)
        {
        abortLaunch("failure in GetModuleFileName()");
        }

    result = allocBuffer(len);
    strncpy(result, localBuf, len);
    return result;
    }

void execJava(const char* javaPath, const char* javaOpts,
              const char* jarPath, int argc, const char * argv[])
    {
    #ifdef DEBUG
    printf("javaPath=%s, javaOpts=%s, jarPath=%s, argc=%d, argv=\n", javaPath, javaOpts, jarPath, argc);
    for (int i = 0; i < argc; ++i)
        {
        printf("[%d] = \"%s\"\n", i, argv[i]);
        }
    #endif // DEBUG

    assert(javaPath != NULL && *javaPath != '\0');
    assert(jarPath  != NULL && *jarPath  != '\0');
    if (javaOpts == NULL)
        {
        javaOpts = "";
        }

    // make the executable file name into the first arg without a path or extension
    assert(argc >= 1);
    argv[0] = removeExtension(extractFile(argv[0]));

    // collect all arguments into one giant string, starting with the call to java
    int len = strlen(javaPath)
            + 1
            + strlen(javaOpts)
            + strlen(" -jar ")
            + strlen(jarPath);
    for (int i = 0; i < argc; ++i)
        {
        len += strlen(argv[i]);
        }

    char* cmd = malloc(len);
    registerGarbage(cmd);

    strcpy(cmd, javaPath);
    strcat(cmd, " ");
    strcat(cmd, javaOpts);
    strcat(cmd, " -jar ");
    strcat(cmd, jarPath);
    for (int i = 0; i < argc; ++i)
        {
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
    if (!CreateProcess(NULL, cmd, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi))
        {
        abortLaunch(cmd);
        }

    WaitForSingleObject(pi.hProcess, INFINITE);

    // the handles need to be released; this isn't killing the process
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    }
