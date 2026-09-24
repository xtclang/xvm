package org.xvm.api;

import java.nio.file.Path;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Lexer;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.StageMgr;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.tool.Console;
import org.xvm.tool.Launcher;
import org.xvm.tool.LauncherOptions.CompilerOptions;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.xvm.asm.ErrorListener.NOWHERE;
import static org.xvm.asm.ErrorListener.Silence.DISCARD;
import static org.xvm.asm.ErrorListener.silent;

/** A missing listener is rejected at the boundary, before work can silently lose diagnostics. */
class ErrorListenerBoundaryTest {
    @Test
    void parserAndLexerRejectMissingListeners() {
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> new Lexer(new Source("module Boundary {}"), null)).getMessage());
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> new Parser(new Source("module Boundary {}"), null)).getMessage());
    }

    @Test
    void compilerAndStageManagersRejectMissingListeners() {
        var module = module();
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> new Compiler(module, null)).getMessage());
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> new StageMgr(module, Stage.Registered, null)).getMessage());
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> new StageMgr(List.<AstNode>of(module), Stage.Registered, null)).getMessage());
    }

    @Test
    void embeddingRejectsMissingListenersBeforeCompilationOrFileAccess(@TempDir Path directory) {
        var support = new EmbeddingSupport().configure(new BuildRepository(), null);
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> support.compile("module Boundary {}", null, null)).getMessage());
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> support.compile(directory.resolve("missing.x").toFile(), null, null, null))
                .getMessage());
    }

    @Test
    void launcherRejectsMissingListenersBeforeDispatch() {
        var console = new Console() {};
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> Launcher.launch("build", new String[]{"--help"}, console, null))
                .getMessage());
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> Launcher.launch(new CompilerOptions.Builder().build(), console, null))
                .getMessage());
    }

    @Test
    void resolutionCollectorsMustChooseAndRetainTheirDestination() throws NoSuchMethodException {
        assertFalse(ResolutionCollector.class.getMethod("getErrorListener").isDefault());
        assertEquals("errs", assertThrows(NullPointerException.class,
                () -> new SimpleCollector(null)).getMessage());
        var errors = new ErrorList();
        var collector = new SimpleCollector(errors);
        assertSame(errors, collector.getErrorListener());
        collector.getErrorListener().error(Parser.MISSING_SEMICOLON, NOWHERE);
        assertEquals(1, errors.getSeriousErrorCount());
    }

    @Test
    void stageManagersRetainTheChosenListenerIncludingExplicitSilence() {
        var module = module();
        var errors = new ErrorList();
        ErrorListener discard = silent(DISCARD);
        assertSame(errors, new StageMgr(module, Stage.Registered, errors).getErrorListener());
        assertSame(discard, new StageMgr(List.<AstNode>of(module), Stage.Registered, discard).getErrorListener());
        module.log(discard, Severity.ERROR, Parser.MISSING_SEMICOLON);
        assertFalse(discard.hasSeriousErrors());
    }

    @Test
    void astReportsCannotSilentlyAcceptAMissingListener() {
        var module = module();
        assertThrows(NullPointerException.class,
                () -> module.log(null, Severity.ERROR, Parser.MISSING_SEMICOLON));
    }

    private static TypeCompositionStatement module() {
        return (TypeCompositionStatement) new Parser(new Source("module Boundary {}"), new ErrorList())
                .parseSource().getStatements().getLast();
    }
}
