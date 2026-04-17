package org.xtclang.plugin.runtime;

import java.io.File;
import java.util.List;

public record DirectTestRequest(
        File projectDir,
        File outputDir,
        List<File> modulePath,
        boolean showVersion,
        boolean verbose,
        String moduleName,
        String methodName,
        List<String> moduleArgs) {

    public DirectTestRequest {
        modulePath = List.copyOf(modulePath);
        moduleArgs = List.copyOf(moduleArgs);
    }
}
