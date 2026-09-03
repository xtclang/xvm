package org.xvm.runtime.template._native.io;

import java.io.PrintStream;

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

import org.xvm.runtime.template.text.xString.StringHandle;

/**
 * The injectable "Console" that prints to the specified PrintStream.
 */
public class xExternalConsole
        extends xTerminalConsole {
    public static xExternalConsole INSTANCE;

    public xExternalConsole(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    /**
     * Register an external console as a named native resource.
     *
     * @return the ID contained in the registered resource name
     */
    public static long register(NativeContainer container, PrintStream out) {
        long id = CONSOLE_ID.incrementAndGet();
        container.addResourceSupplier(
                new InjectionKey(consoleName(id), INSTANCE.getCanonicalType()),
                (frame, opts) -> INSTANCE.ensureConsole(frame, out));
        return id;
    }

    /**
     * Remove a named external console resource.
     */
    public static void unregister(NativeContainer container, long id) {
        container.removeResourceSupplier(
                new InjectionKey(consoleName(id), INSTANCE.getCanonicalType()));
    }

    private static String consoleName(long id) {
        return "console_" + id;
    }

    /**
     * Injection support.
     */
    public ObjectHandle ensureConsole(Frame frame, PrintStream out) {
        ServiceContext   ctx = f_container.createServiceContext("Console");
        ClassComposition clz = getCanonicalClass();

        ConsoleHandle hConsole = new ConsoleHandle(clz.maskAs(getCanonicalType()), ctx, out);
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

            PrintStream out     = hConsole.f_out;
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

        case "readLine":
            return frame.raiseException(xException.unsupported(frame));
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    // ----- ObjectHandle --------------------------------------------------------------------------

    protected static class ConsoleHandle
            extends ServiceHandle {
        protected final PrintStream f_out;

        protected ConsoleHandle(TypeComposition clazz, ServiceContext context, PrintStream out) {
            super(clazz, context);

            f_out = out;
        }
    }

    /**
     * Atomic counter for injected Console objects.
     */
    private static final AtomicLong CONSOLE_ID = new AtomicLong();
}
