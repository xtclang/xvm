package org.xvm.plugin.launchers;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Deque;

public class StreamStore {
    final Deque<InputStream> ins = new ArrayDeque<>();
    final Deque<PrintStream> outs = new ArrayDeque<>();
    final Deque<PrintStream> errs = new ArrayDeque<>();

    StreamStore redirectIn(final InputStream in) {
        redirectAll(in, System.out, System.err);
        return this;
    }

    StreamStore redirectOut(final PrintStream out, final PrintStream err) {
        redirectAll(System.in, out, err);
        return this;
    }

    StreamStore redirectAll(final InputStream in, final PrintStream out, final PrintStream err) {
        ins.push(System.in);
        outs.push(System.out);
        errs.push(System.err);
        System.setIn(in);
        System.setOut(out);
        System.setErr(err);
        return this;
    }

    StreamStore restoreAll(final InputStream in, final PrintStream out, final PrintStream err) {
        System.setIn(ins.pop());
        System.setOut(outs.pop());
        System.setErr(errs.pop());
        return this;
    }

    int size() {
        assert ins.size() == outs.size() && outs.size() == errs.size();
        return ins.size();
    }
}
