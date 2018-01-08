package org.xvm.runtime.template.Clock;


import java.util.Timer;
import java.util.TimerTask;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.Function.FunctionHandle;
import org.xvm.runtime.template.Function.NativeMethodHandle;


/**
 * TODO:
 */
public class xRuntimeClock
        extends ClassTemplate
    {
    public static Timer TIMER = new Timer("RuntimeClock", true);

    public xRuntimeClock(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
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
