package org.xvm.runtime.template._native;


import java.util.Timer;
import java.util.TimerTask;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.LongLong;
import org.xvm.runtime.template.collections.xArray.GenericArrayHandle;
import org.xvm.runtime.template.xBaseInt128.LongLongHandle;
import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xFunction.NativeMethodHandle;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xUInt128;


/**
 * Native implementation of a simple timer (stop-watch) using Java's millisecond-resolution "System"
 * clock.
 */
public class xLoResRealTimeTimer
        extends ClassTemplate
    {
    public static Timer TIMER = xLoResRealTimeClock.TIMER;

    public xLoResRealTimeTimer(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
        }

    @Override
    public void initDeclared()
        {
        markNativeGetter("elapsed");
        markNativeMethod("pause" , new String[0], null);
        markNativeMethod("resume", new String[0], null);
        markNativeMethod("exit"  , new String[0], null);
        markNativeMethod("scheduleAlarm", new String[]{"Duration", "Timer.Alarm"}, null);
        }

    @Override
    protected int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        if (sPropName.equals("elapsed"))
            {
            return frame.assignValue(iReturn, ());
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "scheduleAlarm": // alarm, wakeUp
                {
                GenericHandle  hWakeup = (GenericHandle) ahArg[0];
                FunctionHandle hAlarm  = (FunctionHandle) ahArg[1];

                // assert (hWakeup.timezone == NoTZ) == (this.timezone == NoTZ)
                LongLongHandle llEpoch = (LongLongHandle) hWakeup.getField("epochPicos");

                long ldtNow    = System.currentTimeMillis();
                long ldtWakeup = llEpoch.getValue().divUnsigned(1_000_000_000).getLowValue();

                long cDelay = Math.max(0, ldtWakeup - ldtNow);

                CancellableTask task = new CancellableTask(frame, hAlarm, ldtWakeup);

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

    // -----  helpers -----

    protected GenericHandle dateTimeNow()
        {
        TypeComposition clzDateTime = ensureDateTimeClass();
        GenericHandle hDateTime = new GenericHandle(clzDateTime);

        LongLong llNow = new LongLong(System.currentTimeMillis()).mul(PICOS_PER_MILLI);
        hDateTime.setField("epochPicos", xUInt128.INSTANCE.makeLongLong(llNow));
        hDateTime.setField("timezone", timezone());

        return hDateTime;
        }

    protected GenericHandle timezone()
        {
        GenericHandle hTimeZone = m_hTimeZone;
        if (hTimeZone == null)
            {
            ConstantPool    pool           = ConstantPool.getCurrentPool();
            ClassStructure  structTimeZone = f_templates.getClassStructure("TimeZone");
            TypeConstant    typeTimeZone   = structTimeZone.getCanonicalType();
            TypeComposition clzTimeZone    = f_templates.resolveClass(typeTimeZone);

            ClassStructure  structRule     = (ClassStructure) structTimeZone.getChild("Rule");
            TypeConstant    typeRule       = structRule.getCanonicalType();
            TypeConstant    typeRuleArray  = pool.ensureParameterizedTypeConstant(pool.typeArray(), typeRule);
            TypeComposition clzRuleArray   = f_templates.resolveClass(typeRuleArray);

            m_hTimeZone = hTimeZone = new GenericHandle(clzTimeZone);

            long lOffset = 0; // TODO
            hTimeZone.setField("picos", xInt64.makeHandle(lOffset));
            hTimeZone.setField("name", xNullable.NULL);
            hTimeZone.setField("rules", new GenericArrayHandle(clzRuleArray, Utils.OBJECTS_NONE));
            }

        return hTimeZone;
        }

    protected TypeComposition ensureDateTimeClass()
        {
        TypeComposition clz = m_clzDuration;
        if (clz == null)
            {
            clz = m_clzDuration =
                f_templates.getTemplate("DateTime").getCanonicalClass();
            }
        return clz;
        }

    protected static class CancellableTask
            extends TimerTask
        {
        final private Frame f_frame;
        final private FunctionHandle f_hFunction;
        final private long f_ldtWakeup;

        public volatile boolean m_fCanceled;

        public CancellableTask(Frame frame, FunctionHandle hFunction, long ldtWakeup)
            {
            f_frame = frame;
            f_hFunction = hFunction;
            f_ldtWakeup = ldtWakeup;
            }

        @Override
        public void run()
            {
            if (!m_fCanceled)
                {
                // ensure the timer didn't go back
                long ldtNow = System.currentTimeMillis();
                if (ldtNow >= f_ldtWakeup)
                    {
                    f_frame.f_context.callLater(f_hFunction, Utils.OBJECTS_NONE);
                    }
                else
                    {
                    // reschedule
                    TIMER.schedule(this, f_ldtWakeup - ldtNow);
                    }
                }
            }
        }

    /**
     * Cached Duration class.
     */
    private TypeComposition m_clzDuration;

    private static LongLong PICOS_PER_MILLI = new LongLong(1_000_000_000);
    }
