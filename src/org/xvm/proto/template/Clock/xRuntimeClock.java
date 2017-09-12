package org.xvm.proto.template.Clock;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.Op;
import org.xvm.proto.TypeSet;
import org.xvm.proto.Utils;

import org.xvm.proto.template.Function.FunctionHandle;
import org.xvm.proto.template.Function.NativeMethodHandle;

import java.util.Timer;
import java.util.TimerTask;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xRuntimeClock
        extends ClassTemplate
    {
    public static xRuntimeClock INSTANCE;
    public static Timer TIMER = new Timer("RuntimeClock", true);

    public xRuntimeClock(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        // Cancellable scheduleAlarm(Alarm alarm, DateTime timeToWakeUp);
        markNativeMethod("scheduleAlarm", new String[] {"Clock.Alarm", "DateTime"}, null);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "scheduleAlarm": // alarm, delay
                {
                FunctionHandle hAlarm = (FunctionHandle) ahArg[0];
                long cDelay = ((JavaLong) ahArg[1]).getValue();

                CancellableTask task = new CancellableTask(frame, hAlarm);

                TIMER.schedule(task, cDelay);

                FunctionHandle hCancel = new NativeMethodHandle((_frame, _ah, _iReturn) ->
                    {
                    task.m_fCanceled = true;
                    return Op.R_NEXT;
                    });
                return frame.assignValue(iReturn, hCancel);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    protected class CancellableTask
            extends TimerTask
        {
        final private Frame f_frame;
        final private FunctionHandle f_hFunction;

        public volatile boolean m_fCanceled;

        public CancellableTask(Frame frame, FunctionHandle hFunction)
            {
            f_frame = frame;
            f_hFunction = hFunction;
            }

        @Override
        public void run()
            {
            if (!m_fCanceled)
                {
                f_frame.f_context.callLater(f_hFunction, Utils.OBJECTS_NONE);
                }
            }
        }
    }
