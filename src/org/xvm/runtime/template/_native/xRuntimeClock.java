package org.xvm.runtime.template._native;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import java.time.temporal.ChronoUnit;

import java.util.Timer;
import java.util.TimerTask;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xFunction.NativeMethodHandle;
import org.xvm.runtime.template.xInt64;


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
        markNativeGetter("now");
        markNativeMethod("scheduleAlarm", new String[]{"DateTime", "Clock.Alarm"}, null);
        }

    @Override
    protected int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "now":
                return frame.assignValue(iReturn, dateTimeNow());
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

                CancellableTask task = new CancellableTask(frame, hAlarm);

                GenericHandle hDate = (GenericHandle) hWakeup.getField("date");
                GenericHandle hTime = (GenericHandle) hWakeup.getField("time");

                LocalDateTime ldtNow    = LocalDateTime.now();
                LocalDateTime ldtWakeup = LocalDateTime.of(
                    toLocalDate(hDate), toLocalTime(hTime));

                long cDelay = Math.max(0, ldtNow.until(ldtWakeup, ChronoUnit.MILLIS));

                //  TODO: remove -- temporary hack until Duration is implemented
                if (cDelay == 0)
                    {
                    cDelay = 1000;
                    }
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

    protected GenericHandle dateNow()
        {
        return dateOf(LocalDate.now());
        }

    protected GenericHandle dateOf(LocalDate date)
        {
        TypeComposition clzDate = ensureDateClass();
        GenericHandle hDate = new GenericHandle(clzDate);
        hDate.setField("year", xInt64.makeHandle(date.getYear()));
        hDate.setField("month", xInt64.makeHandle(date.getMonthValue()));
        hDate.setField("day", xInt64.makeHandle(date.getDayOfMonth()));
        return hDate;
        }

    protected LocalDate toLocalDate(GenericHandle hDate)
        {
        JavaLong hYear  = (JavaLong) hDate.getField("year");
        JavaLong hMonth = (JavaLong) hDate.getField("month");
        JavaLong hDay   = (JavaLong) hDate.getField("day");
        return LocalDate.of((int) hYear.getValue(), (int) hMonth.getValue(),
                            (int) hDay.getValue());
        }

    protected GenericHandle timeNow()
        {
        return timeOf(LocalTime.now());
        }

    protected GenericHandle timeOf(LocalTime time)
        {
        TypeComposition clzTime = ensureTimeClass();
        GenericHandle hTime = new GenericHandle(clzTime);
        hTime.setField("hour", xInt64.makeHandle(time.getHour()));
        hTime.setField("minute", xInt64.makeHandle(time.getMinute()));
        hTime.setField("second", xInt64.makeHandle(time.getSecond()));
        hTime.setField("nano", xInt64.makeHandle(time.getNano()));
        return hTime;
        }

    protected LocalTime toLocalTime(GenericHandle hTime)
        {
        JavaLong hHour   = (JavaLong) hTime.getField("hour");
        JavaLong hMinute = (JavaLong) hTime.getField("minute");
        JavaLong hSecond = (JavaLong) hTime.getField("second");
        JavaLong hNano   = (JavaLong) hTime.getField("nano");
        return LocalTime.of((int) hHour.getValue(), (int) hMinute.getValue(),
                            (int) hSecond.getValue(), (int) hNano.getValue());
        }

    protected GenericHandle dateTimeNow()
        {
        TypeComposition clzDateTime = ensureDateTimeClass();
        GenericHandle hDateTime = new GenericHandle(clzDateTime);
        hDateTime.setField("date", dateNow());
        hDateTime.setField("time", timeNow());
        return hDateTime;
        }

    protected TypeComposition ensureDateClass()
        {
        TypeComposition clz = m_clzDate;
        if (clz == null)
            {
            clz = m_clzDate =
                f_templates.getTemplate("Date").getCanonicalClass();
            }
        return clz;
        }

    protected TypeComposition ensureTimeClass()
        {
        TypeComposition clz = m_clzTime;
        if (clz == null)
            {
            clz = m_clzTime =
                f_templates.getTemplate("Time").getCanonicalClass();
            }
        return clz;
        }

    protected TypeComposition ensureDateTimeClass()
        {
        TypeComposition clz = m_clzDateTime;
        if (clz == null)
            {
            clz = m_clzDateTime =
                f_templates.getTemplate("DateTime").getCanonicalClass();
            }
        return clz;
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

    /**
     * Cached Date class.
     */
    private TypeComposition m_clzDate;
    /**
     * Cached Time class.
     */
    private TypeComposition m_clzTime;
    /**
     * Cached DateTime class.
     */
    private TypeComposition m_clzDateTime;
    }
