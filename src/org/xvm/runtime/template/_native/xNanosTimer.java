package org.xvm.runtime.template._native;


import java.util.Timer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.LongLong;
import org.xvm.runtime.template.xBaseInt128.LongLongHandle;
import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xFunction.NativeFunctionHandle;
import org.xvm.runtime.template.xUInt128;

import org.xvm.runtime.template._native.xLocalClock.Alarm;

import static org.xvm.runtime.template._native.xLocalClock.PICOS_PER_MILLI;


/**
 * Native implementation of a simple timer (stop-watch) using Java's nanosecond-resolution "System"
 * clock.
 *
 * TODO API changes
 */
public class xNanosTimer
        extends ClassTemplate
    {
    public xNanosTimer(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
        m_nStart = System.nanoTime();
        }

    @Override
    public void initDeclared()
        {
        markNativeGetter("elapsed");
        markNativeMethod("pause" , new String[0], null);
        markNativeMethod("resume", new String[0], null);
        markNativeMethod("reset" , new String[0], null);
        markNativeMethod("scheduleAlarm", new String[]{"Duration", "Timer.Alarm"}, null);
        }

    @Override
    protected int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "elapsed":
                {
                return frame.assignValue(iReturn, elapsedDuration());
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "pause":
                {
                if (m_nStart != 0)
                    {
                    long cAdd = System.nanoTime() - m_nStart;
                    if (cAdd > 0)
                        {
                        m_cNanosPrevSum += cAdd;
                        }

                    m_nStart = 0;
                    }
                return Op.R_NEXT;
                }

            case "resume":
                {
                if (m_nStart == 0)
                    {
                    m_nStart = System.nanoTime();
                    }
                return Op.R_NEXT;
                }

            case "reset":
                {
                m_cNanosPrevSum = 0;
                if (m_nStart != 0)
                    {
                    m_nStart = System.nanoTime();
                    }
                return Op.R_NEXT;
                }

            case "scheduleAlarm": // duration, alarm
                {
                GenericHandle  hDuration = (GenericHandle ) ahArg[0];
                FunctionHandle hAlarm    = (FunctionHandle) ahArg[1];

                // note: the Java Timer uses millisecond scheduling, but we're given scheduling
                // instructions in picoseconds
                LongLongHandle  llPicos = (LongLongHandle) hDuration.getField("picosecondsTotal");
                long            cMillis = Math.max(0, llPicos.getValue().divUnsigned(PICOS_PER_MILLI).getLowValue());
                Alarm           task    = new Alarm(frame, hAlarm);
                TIMER.schedule(task, cMillis);

                FunctionHandle hCancel = new NativeFunctionHandle((_frame, _ah, _iReturn) ->
                    {
                    task.cancel();
                    return Op.R_NEXT;
                    });
                return frame.assignValue(iReturn, hCancel);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    // -----  helpers -----

    protected GenericHandle elapsedDuration()
        {
        long cNanosTotal = m_cNanosPrevSum;
        if (m_nStart != 0)
            {
            long cAdd = System.nanoTime() - m_nStart;
            if (cAdd > 0)
                {
                cNanosTotal += cAdd;
                }
            }

        LongLong      llPicos   = new LongLong(cNanosTotal).mul(PICOS_PER_NANO_LL);
        GenericHandle hDuration = new GenericHandle(ensureDurationClass());
        hDuration.setField("picosecondsTotal", xUInt128.INSTANCE.makeLongLong(llPicos));

        return hDuration;
        }

    protected TypeComposition ensureDurationClass()
        {
        TypeComposition clz = m_clzDuration;
        if (clz == null)
            {
            clz = m_clzDuration = f_templates.getTemplate("Duration").getCanonicalClass();
            }
        return clz;
        }

    /**
     * Cached Duration class.
     */
    private TypeComposition m_clzDuration;

    /**
     * The number of nanoseconds previously accumulated before the timer was paused.
     */
    private long m_cNanosPrevSum;

    /**
     * The value (in nanos) when the timer was started, or 0 if the timer is paused.
     */
    private long m_nStart;

    protected static final Timer    TIMER             = xLocalClock.TIMER;
    protected static final long     PICOS_PER_NANO    = 1_000;
    protected static final LongLong PICOS_PER_NANO_LL = new LongLong(PICOS_PER_NANO);
    }
