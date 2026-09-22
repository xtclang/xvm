package org.xtclang.plugin.runtime.impl;

import static java.nio.file.Files.isDirectory;

import org.xvm.tool.LauncherOptions.CompilerOptions;

import org.xtclang.plugin.runtime.DirectCompileRequest;

final class IsolatedLauncherOptionsBuilder {

    CompilerOptions buildCompilerOptions(final DirectCompileRequest request) {
        final var outputDir = request.outputDir().toPath();

        final var builder = CompilerOptions.builder()
            .forceRebuild(request.rebuild())
            .enableShowVersion(request.showVersion())
            .enableVerbose(request.verbose())
            .disableWarnings(request.disableWarnings())
            .enableStrictMode(request.strict())
            .qualifyOutputNames(request.qualifiedOutputName())
            .setOutputLocation(outputDir.toString());

        final var resourceDir = request.resourceDir();
        if (resourceDir != null && isDirectory(resourceDir.toPath())) {
            builder.addResourceLocation(resourceDir.toString());
        }

        request.modulePath().forEach(path -> builder.addModulePath(path.getAbsolutePath()));
        request.sourceFiles().forEach(file -> builder.addInputFile(file.getAbsolutePath()));

        final var moduleVersion = request.xtcVersion();
        if (moduleVersion != null && !moduleVersion.isBlank()) {
            builder.setModuleVersion(moduleVersion);
        }
        return builder.build();
    }

}
