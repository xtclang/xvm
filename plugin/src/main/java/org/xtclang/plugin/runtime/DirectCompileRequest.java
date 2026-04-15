package org.xtclang.plugin.runtime;

import java.io.File;
import java.util.List;

public record DirectCompileRequest(
        File projectDir,
        File outputDir,
        File resourceDir,
        List<File> modulePath,
        List<File> sourceFiles,
        boolean rebuild,
        boolean showVersion,
        boolean verbose,
        boolean disableWarnings,
        boolean strict,
        boolean qualifiedOutputName,
        String xtcVersion) {

    public DirectCompileRequest {
        modulePath = List.copyOf(modulePath);
        sourceFiles = List.copyOf(sourceFiles);
    }
}
