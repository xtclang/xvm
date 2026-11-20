package org.xtclang.plugin.runtime.impl;

import java.io.File;
import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.ModuleRepository;
import org.xvm.tool.Compiler;
import org.xvm.tool.Console;
import org.xvm.tool.LauncherOptions.CompilerOptions;

/**
 * The command-line compiler, linking against the library modules that the in-process compiles of a build share.
 */
final class SharedLibraryCompiler extends Compiler {
    private final LibraryModules libraries;

    SharedLibraryCompiler(final CompilerOptions options, final Console console, final ErrorListener errs,
                          final LibraryModules libraries) {
        super(options, console, errs);
        this.libraries = libraries;
    }

    @Override
    protected ModuleRepository configureLibraryRepo(final List<File> path) {
        return libraries.libraryRepository(path);
    }
}
