package org.xtclang.plugin.runtime;

import java.io.File;
import java.util.List;

public record DirectTestRequest(
        File projectDir,
        File outputDir,
        List<File> modulePath,
        boolean showVersion,
        boolean verbose,
        boolean jit,
        String moduleName,
        String methodName,
        List<String> moduleArgs,
        List<String> testPackages,
        List<String> testClasses,
        List<String> testMethods,
        String testReport) {

    public DirectTestRequest {
        modulePath = List.copyOf(modulePath);
        moduleArgs = List.copyOf(moduleArgs);
        testPackages = List.copyOf(testPackages);
        testClasses = List.copyOf(testClasses);
        testMethods = List.copyOf(testMethods);
    }
}
