package org.xvm.api;

import java.io.File;
import java.io.PrintWriter;

import java.time.Instant;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

/**
 * JIT-backed management and monitoring for one runner task.
 */
class JitControl
        implements EmbeddingSupport.Control {
    static Connector createConnector(ModuleRepository repository) {
        throw unsupported();
    }

    static EmbeddingSupport.Control create(Connector connector, ModuleStructure module,
                                           ModuleRepository repository, PrintWriter console,
                                           File rootDir, ErrorListener errs) {
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
    public Long result() {
        throw unsupported();
    }

    @Override
    public void close() {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("JIT run control is not implemented");
    }
}
