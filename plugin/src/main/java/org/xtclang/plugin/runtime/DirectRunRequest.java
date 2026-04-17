package org.xtclang.plugin.runtime;

import java.io.File;
import java.util.List;

public record DirectRunRequest(
        File projectDir,
        List<File> modulePath,
        boolean showVersion,
        boolean verbose,
        String moduleName,
        String methodName,
        List<String> moduleArgs) {

    public DirectRunRequest {
        modulePath = List.copyOf(modulePath);
        moduleArgs = List.copyOf(moduleArgs);
    }
}
