#include <string.h>
#include <mach-o/dyld.h>
#include "launcher.h"
#include "os_specific.h"


const char* findLauncherPath() {
    char* result = NULL;

    char localBuf[32];
    uint32_t size = 32;
    int err = _NSGetExecutablePath(localBuf, &size);
    switch (err) {
    case 0:
        result = allocBuffer(strlen(localBuf));
        strcpy(result, localBuf);
        break;

    case -1:
        result = allocBuffer(size);
        if (_NSGetExecutablePath(result, &size) != 0) {
            abortLaunch("failure in _NSGetExecutablePath()");
        }
        break;

    default:
        abortLaunch("failure in initial _NSGetExecutablePath()");
    }

    return result;
}
