package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener.ErrorInfo;

import org.xvm.compiler.CompilerException;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Recording a diagnostic and deciding to stop are two questions, and this pins that they are asked
 * separately.
 *
 * <p>They used to be one. {@code ErrorListener.log} returned a boolean meaning "abort", so a host
 * that only wanted to watch had no correct value to return - {@code false} suppressed a legitimate
 * abort, {@code true} invented one - and {@code ErrorListener.RUNTIME} went further and threw from
 * inside {@code log}, pre-empting whatever the detecting code meant to throw next.
 *
 * <p>{@code log} is now {@code void}. {@link ErrorListener#isAbortDesired()} is the only question
 * about control flow, and the code that detects a problem is the code that decides to stop.
 */
public class AbortIsNotLoggedTest {
    /**
     * The compiler still gives up when the listener says to. This is the half that a mechanical
     * "stop reading log()'s result" change could silently break, so it is checked against the real
     * Parser rather than against a listener in isolation.
     */
    @Test
    public void theParserStillAbortsWhenTheListenerSaysSo() {
        var strict = ErrorList.firstError();

        assertThrows(CompilerException.class,
                () -> new Parser(brokenSource(), strict).parseSource(),
                "a firstError() list asks to abort after one error, and the parser must obey");

        assertTrue(strict.isAbortDesired());
        assertTrue(strict.hasSeriousErrors());
    }

    /**
     * ...and does not give up when it does not. Same source, a list that tolerates errors.
     */
    @Test
    public void theParserCarriesOnWhenTheListenerDoesNot() {
        var tolerant = ErrorList.unlimited();

        new Parser(brokenSource(), tolerant).parseSource();

        assertFalse(tolerant.isAbortDesired(), "an unlimited list never asks to abort on count");
        assertTrue(tolerant.hasSeriousErrors(), "but it still recorded the problem");
    }

    /**
     * The point of the change: an observer cannot change what the compiler does. There is no longer
     * a value it could return that would suppress or invent an abort, and a stateless listener
     * answers "no" to the only question that matters - which is correct, because an observer has no
     * basis for stopping a compilation.
     */
    @Test
    public void anObserverCannotDecideControlFlow() {
        var seen = new ArrayList<String>();

        // this compiles ONLY because log() is void
        ErrorListener observer = err -> seen.add(err.getCode());

        new Parser(brokenSource(), observer).parseSource();

        assertFalse(seen.isEmpty(), "the observer was told");
        assertFalse(observer.isAbortDesired(), "and was not asked to decide");
    }

    /**
     * {@code ErrorListener.RUNTIME} is what every unconfigured pool and container answers with, so
     * it is the listener most asm and runtime code actually reaches. It used to throw
     * IllegalStateException from inside log() at ERROR and above - which meant the same diagnostic
     * behaved differently at run time than at compile time, and pre-empted the exception the
     * detecting code intended to raise. It reports now, and that is all.
     */
    @Test
    public void theRuntimeListenerReportsRatherThanThrowing() {
        for (Severity severity : List.of(Severity.INFO, Severity.WARNING,
                                         Severity.ERROR, Severity.FATAL)) {
            ErrorListener.RUNTIME.log(new ErrorInfo(severity, Constants.VE_UNKNOWN,
                    new Object[] {"reporting is not aborting"}, (XvmStructure) null));
        }

        assertFalse(ErrorListener.RUNTIME.isAbortDesired(),
                "a stateless listener has no basis for aborting, at any severity");
    }

    /**
     * A branch keeps its parent's answer to the control-flow question, which is what lets cascade
     * suppression collect without changing whether the compiler gives up.
     */
    @Test
    public void aBranchCarriesTheAbortDecision() {
        var strict = ErrorList.firstError();
        var branch = strict.branch(null);

        branch.log(Severity.ERROR, Constants.VE_UNKNOWN, (XvmStructure) null, "in the branch");

        assertTrue(branch.isAbortDesired(), "the branch collected an error and answers for itself");
        assertFalse(strict.isAbortDesired(), "and has not surfaced it, because nothing merged it");

        branch.merge();
        assertTrue(strict.isAbortDesired(), "merging is what promotes it");
        assertEquals(1, strict.getErrors().size());
    }

    private static Source brokenSource() {
        // Two recoverable errors, so "abort after the first" is distinguishable from "collect them
        // all". Recoverable matters: Parser.expect() throws a CompilerException of its own for
        // errors it cannot resume from, which would mask the abort this test is about.
        return new Source("""
                module Broken {
                    @Inject Console console;

                    void one() {
                        console.print("one")
                    }

                    void two() {
                        console.print("two")
                    }
                }
                """);
    }
}
