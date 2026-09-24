package org.xvm.asm;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.ErrorListener.Silence;
import org.xvm.asm.ErrorListener.Site;

import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Lexer;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xvm.asm.ErrorListener.NOWHERE;

/** Migration examples that compile independently of later listener-ownership changes. */
class ErrorListenerMigrationTest {
    @Test
    void collectingDeliversDuplicatesWhileErrorListCollapsesThem() {
        var received = new ArrayList<ErrorInfo>();
        var collector = ErrorListener.collecting(received::add);
        var errors = new ErrorList();
        var both = ErrorListener.tee(collector, errors);
        both.error("PARSER-03", NOWHERE, "a", "b");
        both.error("PARSER-03", NOWHERE, "a", "b");
        assertEquals(2, received.size());
        assertEquals(1, errors.getErrors().size());
        assertTrue(collector.hasSeriousErrors());
        assertFalse(collector.isAbortDesired());
    }

    @Test
    void branchesAndDerivedSilenceObserveTheParentBudget() {
        var parent = new ErrorList(ErrorList.FIRST_ERROR);
        var branch = parent.branch(null).branch(null);
        var quiet = parent.silence(Silence.PROBE);
        parent.error("PARSER-03", NOWHERE, "a", "b");
        assertTrue(branch.isAbortDesired());
        assertTrue(quiet.isAbortDesired());
        quiet.error("PARSER-03", NOWHERE, "discarded", "report");
        assertEquals(1, parent.getErrors().size());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyStructureReportsStillUseTheBranchSourceSite() {
        var source = new Source("module Example {}");
        var errors = new ErrorList();
        var node = new Parser(source, errors).parseSource().getStatements().getLast();
        var branch = errors.branch(node);
        var structure = new FileStructure("Example").getModule();
        branch.log(Severity.ERROR, "PARSER-03", new Object[]{"legacy", "report"}, structure);
        branch.error("PARSER-03", ErrorListener.at(structure), "new", "report");
        branch.merge();
        assertEquals(2, errors.getErrors().size());
        for (var error : errors.getErrors()) {
            var site = assertInstanceOf(Site.In.class, error.site());
            assertSame(source, site.source());
            assertEquals(node.getStartPosition(), site.lPosStart());
            assertEquals(node.getEndPosition(), site.lPosEnd());
        }
    }

    @Test
    void parserStillStopsWhenReportingSpendsTheBudget() {
        var errors = new ErrorList(ErrorList.FIRST_ERROR);
        var parser = new Parser(new Source("module Example { void run() { Int x = ; Int y = ; } }"), errors);
        assertThrows(CompilerException.class, parser::parseSource);
        assertEquals(1, errors.getSeriousErrorCount());
        assertTrue(errors.isAbortDesired());
    }

    @Test
    void lexerStillStopsWhenReportingSpendsTheBudget() {
        var errors = new ErrorList(ErrorList.FIRST_ERROR);
        var lexer = new Lexer(new Source("\"unterminated"), errors);
        assertThrows(CompilerException.class, () -> lexer.forEachRemaining(token -> {}));
        assertEquals(1, errors.getSeriousErrorCount());
        assertTrue(errors.isAbortDesired());
    }
}
