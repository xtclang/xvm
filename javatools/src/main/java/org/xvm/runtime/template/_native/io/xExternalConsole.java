package org.xvm.runtime.template._native.io;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

/**
 * The injectable "Console" that prints to the specified PrintWriter and reads lines from the host's
 * {@link Input}.
 */
public class xExternalConsole
        extends xTerminalConsole {
    public static xExternalConsole INSTANCE;

    /**
     * The host's input for a console.
     */
    @FunctionalInterface
    public interface Input {
        /**
         * Read the next line. This is called on one of the runtime's IO threads, so it may block
         * until the host has a line.
         *
         * @param suppressEcho  true when the application asks that the line not be shown as it is
         *                      typed, as for a password; how to honor that is up to the host
         *
         * @return the line, or null at the end of the input
         *
         * @throws UnsupportedOperationException if the host has no input for this console
         */
        String readLine(boolean suppressEcho) throws IOException;
    }

    public xExternalConsole(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    /**
     * Register an external console as a named native resource.
     *
     * @param out  the output for the console
     * @param in   the input for {@code readLine}; its end reads as an empty line, and a host without
     *             input throws UnsupportedOperationException. A read still waiting when the
     *             application ends returns once the host ends this input
     *
     * @return the ID contained in the registered resource name
     */
    public static long register(NativeContainer container, PrintWriter out, Input in) {
        long id = CONSOLE_ID.incrementAndGet();
        container.addResourceSupplier(
                new InjectionKey(consoleName(id), INSTANCE.getCanonicalType()),
                (frame, opts) -> INSTANCE.ensureConsole(frame, out, in));
        return id;
    }

    /**
     * Remove a named external console resource.
     */
    public static void unregister(NativeContainer container, long id) {
        container.removeResourceSupplier(
                new InjectionKey(consoleName(id), INSTANCE.getCanonicalType()));
    }

    /**
     * @return how many named external console resources are registered, for a host that checks
     *         that ended runs leave none behind
     */
    public static int registeredCount(NativeContainer container) {
        return (int) container.getResourceNames().stream().filter(name -> name.startsWith(CONSOLE_PREFIX)).count();
    }

    private static String consoleName(long id) {
        return CONSOLE_PREFIX + id;
    }

    /**
     * Injection support.
     */
    public ObjectHandle ensureConsole(Frame frame, PrintWriter out, Input in) {
        ServiceContext   ctx = f_container.createServiceContext("Console");
        ClassComposition clz = getCanonicalClass();

        ConsoleHandle hConsole = new ConsoleHandle(clz.maskAs(getCanonicalType()), ctx, out, in);
        ctx.setService(hConsole);
        return hConsole;
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        ConsoleHandle hConsole = (ConsoleHandle) hTarget;
        switch (method.getName()) {
        case "print": { // Object o = "", Boolean suppressNewline = False
            boolean fNewline = ahArg[1] != xBoolean.TRUE;

            ObjectHandle hVal = ahArg[0];
            if (hVal == ObjectHandle.DEFAULT) {
                if (fNewline) {
                    hConsole.f_out.println();
                }
                return Op.R_NEXT;
            }

            PrintWriter out     = hConsole.f_out;
            int         iResult = Utils.callToString(frame, hVal);
            switch (iResult) {
            case Op.R_NEXT: {
                char[] ach = ((StringHandle) frame.popStack()).getValue();
                if (fNewline) {
                    out.println(ach);
                } else {
                    out.print(ach);
                    out.flush();
                }
                return Op.R_NEXT;
            }

            case Op.R_CALL:
                Frame.Continuation stepNext = fNewline
                    ? frameCaller -> {
                            char[] ach = ((StringHandle) frameCaller.popStack()).getValue();
                            out.println(ach);
                            return Op.R_NEXT;
                        }
                    : frameCaller -> {
                            char[] ach = ((StringHandle) frameCaller.popStack()).getValue();
                            out.print(ach);
                            out.flush();
                            return Op.R_NEXT;
                        };
                frame.m_frameNext.addContinuation(stepNext);
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return iResult;
            }
        }

        case "readLine": // String prompt = "", Boolean suppressEcho = False
            return invokeReadLine(frame, hConsole, ahArg, iReturn);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    /**
     * Read a line from the host's input, passing "suppressEcho" to the host, which shows the input.
     * The read waits on an IO thread, so a program waiting for input does not hold a service worker.
     */
    private int invokeReadLine(Frame frame, ConsoleHandle hConsole, ObjectHandle[] ahArg,
                               int iReturn) {
        Input   in            = hConsole.f_in;
        String  sPrompt       = ahArg[0] instanceof StringHandle hString ? hString.getStringValue() : "";
        boolean fSuppressEcho = ahArg[1] == xBoolean.TRUE;
        if (!sPrompt.isEmpty()) {
            hConsole.f_out.print(sPrompt);
            hConsole.f_out.flush();
        }

        CompletableFuture<String> cfLine =
                frame.f_context.f_container.scheduleIO(() -> in.readLine(fSuppressEcho));
        Frame.Continuation continuation = frameCaller -> {
            try {
                String sLine = cfLine.get();
                return frameCaller.assignValue(iReturn, sLine == null
                        ? xString.EMPTY_STRING
                        : xString.makeHandle(sLine));
            } catch (Throwable e) {
                Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
                return frameCaller.raiseException(cause instanceof UnsupportedOperationException
                        ? xException.unsupported(frameCaller)
                        : xException.obscureIoException(frameCaller, cause.getMessage()));
            }
        };
        return frame.waitForIO(cfLine, continuation);
    }

    // ----- ObjectHandle --------------------------------------------------------------------------

    protected static class ConsoleHandle
            extends ServiceHandle {
        protected final PrintWriter f_out;
        protected final Input       f_in;

        protected ConsoleHandle(TypeComposition clazz, ServiceContext context, PrintWriter out,
                                Input in) {
            super(clazz, context);

            f_out = out;
            f_in  = in;
        }
    }

    /**
     * Atomic counter for injected Console objects.
     */
    private static final AtomicLong CONSOLE_ID = new AtomicLong();

    private static final String CONSOLE_PREFIX = "console_";
}
