package org.xvm.api;

import java.io.File;

import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.compiler.BuildRepository;

import org.xvm.tool.Compiler;
import org.xvm.tool.Console;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.ModuleInfo.Node;

/**
 * One embedding compilation request using the standard source-tree and emission pipeline.
 * The session owns serialization and thread-local restoration; compiler state is request-local.
 */
final class FileCompiler extends Compiler {
    FileCompiler(CompilerOptions options, Console console, ErrorListener errors,
                 ModuleRepository core, ModuleRepository input, ModuleRepository output) {
        super(options, console, errors);
        this.core = core;
        this.input = input;
        this.output = output;
    }

    int compile() {
        validateOptions();
        checkErrors("compiler options");
        return process();
    }

    @Override
    protected ModuleRepository configureLibraryRepo(List<File> paths) {
        ModuleRepository files = super.configureLibraryRepo(paths);
        return input == null
                ? new LinkedRepository(true, new BuildRepository(), files, core)
                : new LinkedRepository(true, new BuildRepository(), input, files, core);
    }

    @Override
    protected int emitModules(List<Node> nodes, ModuleRepository destination) {
        return super.emitModules(nodes, output == null ? destination : output);
    }

    private final ModuleRepository core;
    private final ModuleRepository input;
    private final ModuleRepository output;
}
