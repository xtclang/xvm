package org.xvm.api;

import java.io.PrintStream;

import java.time.Instant;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;


/**
 * JIT-backed management and monitoring for one runner task.
 */
class JitControl
        implements LspSupport.Control {
    static Connector createConnector(ModuleRepository repository) {
        throw unsupported();
    }

    static LspSupport.Control create(Connector connector, ModuleStructure module,
                                     ModuleRepository repository, PrintStream console,
                                     ErrorListener errs) {
        throw unsupported();
    }

    @Override
    public boolean running() {
        throw unsupported();
    }

    @Override
    public void join() {
        throw unsupported();
    }

    @Override
    public Instant whenStarted() {
        throw unsupported();
    }

    @Override
    public Instant whenStopped() {
        throw unsupported();
    }

    @Override
    public void kill() {
        throw unsupported();
    }

    @Override
    public Long result() {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("JIT run control is not implemented");
    }
}
