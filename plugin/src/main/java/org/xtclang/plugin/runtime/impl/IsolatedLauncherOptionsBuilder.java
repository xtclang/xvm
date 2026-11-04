package org.xtclang.plugin.runtime.impl;

import static java.nio.file.Files.isDirectory;
import static org.xtclang.plugin.XtcPluginConstants.XUNIT_REPORT_INJECTION;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;
import org.xvm.tool.LauncherOptions.TestRunnerOptions;
import org.xvm.tool.TestRunner;

import org.xtclang.plugin.runtime.DirectCompileRequest;
import org.xtclang.plugin.runtime.DirectRunRequest;
import org.xtclang.plugin.runtime.DirectTestRequest;

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

    RunnerOptions buildRunnerOptions(final DirectRunRequest request) {
        final var builder = RunnerOptions.builder()
            .enableShowVersion(request.showVersion())
            .enableVerbose(request.verbose())
            .enableJit(request.jit())
            .setMethodName(request.methodName())
            .setTarget(request.moduleName(), request.moduleArgs())
            .noRecompile();

        request.modulePath().forEach(path -> builder.addModulePath(path.getAbsolutePath()));
        return builder.build();
    }

    TestRunnerOptions buildTestRunnerOptions(final DirectTestRequest request) {
        final var builder = TestRunnerOptions.builder()
            .setXUnitOutputDirectory(request.outputDir().getAbsolutePath());
        builder.enableShowVersion(request.showVersion())
            .enableVerbose(request.verbose())
            .enableJit(request.jit())
            .setTarget(request.moduleName(), request.moduleArgs())
            .noRecompile();

        request.modulePath().forEach(path -> builder.addModulePath(path.getAbsolutePath()));
        // the selections as the injections xtc test makes of -p, -c and -t, which an older launcher's builder takes
        request.testPackages().forEach(name -> builder.addInjection(TestRunner.XUNIT_TEST_PACKAGES_ARG, name));
        request.testClasses().forEach(name -> builder.addInjection(TestRunner.XUNIT_TEST_CLASSES_ARG, name));
        request.testMethods().forEach(name -> builder.addInjection(TestRunner.XUNIT_TEST_METHODS_ARG, name));
        if (request.testReport() != null) {
            builder.addInjection(XUNIT_REPORT_INJECTION, request.testReport());
        }
        return builder.build();
    }
}
