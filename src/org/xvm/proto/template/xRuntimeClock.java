package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.JavaLong;

import org.xvm.proto.template.xFunction.FunctionHandle;
import org.xvm.proto.template.xFunction.NativeMethodHandle;

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
        // TODO: change when the DateTime is implemented
        getMethodStructure("scheduleAlarm", new String[]{"x:Function", "x:Int64"}, new String[]{"x:Function"}).markNative();
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget, MethodStructure method, ObjectHandle hArg, int iReturn)
        {
        return super.invokeNative(frame, hTarget, method, hArg, iReturn);
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget, MethodStructure method, ObjectHandle[] ahArg, int iReturn)
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

        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
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
