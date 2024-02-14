#include <string.h>
#include <unistd.h>
#include "launcher.h"
#include "os_specific.h"


const char* findLauncherPath() {
    char* result = NULL;

    const size_t size = 512;
    char localBuf[size];
    ssize_t len = readlink("/proc/self/exe", localBuf, size);
    if (len < 0) {
        abortLaunch("failure in readlink()");
    }

    result = allocBuffer(len);
    strncpy(result, localBuf, len);
    return result;
}
