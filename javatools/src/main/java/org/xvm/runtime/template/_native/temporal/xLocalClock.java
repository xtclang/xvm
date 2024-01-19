package org.xvm.runtime.template._native.temporal;


import java.util.Timer;
import java.util.TimerTask;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;
import org.xvm.runtime.WeakCallback;

import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xInt128;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;


/**
 * Native implementation of a simple wall clock using Java's millisecond-resolution "System" clock.
 */
public class xLocalClock
        extends xService
    {
    public static xLocalClock INSTANCE;

    public xLocalClock(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeProperty("now");
        markNativeProperty("timezone");

        markNativeMethod("schedule", new String[]{"temporal.Time", "temporal.Clock.Alarm"}, null);

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().ensureEcstasyTypeConstant("temporal.Clock");
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "now":
                return frame.assignValue(iReturn, timeNow(frame));

            case "timezone":
                return frame.assignValue(iReturn, timezone(frame));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "schedule": // wakeUp, alarm
                {
                GenericHandle  hWakeup = (GenericHandle) ahArg[0];
                FunctionHandle hAlarm  = (FunctionHandle) ahArg[1];

                // assert (hWakeup.timezone == NoTZ) == (this.timezone == NoTZ)
                LongLong llEpoch = ((LongLongHandle) hWakeup.getField(null, "epochPicos")).getValue();
                long     ldtNow    = System.currentTimeMillis();
                long     ldtWakeup = llEpoch.divUnsigned(xNanosTimer.PICOS_PER_MILLI).getLowValue();
                long     cDelay    = Math.max(0, ldtWakeup - ldtNow);

                Alarm alarm = new Alarm(new WeakCallback(frame, hAlarm));
                try
                    {
                    TIMER.schedule(alarm, cDelay);
                    }
                catch (Exception e)
                    {
                    alarm.cancel();
                    return frame.raiseException(e.getMessage());
                    }

                FunctionHandle hCancel = new NativeFunctionHandle((_frame, _ah, _iReturn) ->
                    {
                    alarm.cancel();
                    return Op.R_NEXT;
                    });

                return frame.assignValue(iReturn, hCancel);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Injection support.
     */
    public ObjectHandle ensureLocalClock(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hClock = m_hLocalClock;
        if (hClock == null)
            {
            m_hLocalClock = hClock = createServiceHandle(
                f_container.createServiceContext("LocalClock"),
                    getCanonicalClass(), getCanonicalType());
            }

        return hClock;
        }

    public ObjectHandle ensureDefaultClock(Frame frame, ObjectHandle hOpts)
        {
        // TODO
        return ensureLocalClock(frame, hOpts);
        }

    public ObjectHandle ensureUTCClock(Frame frame, ObjectHandle hOpts)
        {
        // TODO
        return ensureLocalClock(frame, hOpts);
        }


    // -----  helpers ------------------------------------------------------------------------------

    protected GenericHandle timeNow(Frame frame)
        {
        TypeComposition clzTime = ensureTimeClass();
        GenericHandle   hTime   = new GenericHandle(clzTime);

        LongLong llNow = new LongLong(System.currentTimeMillis()).mul(xNanosTimer.PICOS_PER_MILLI_LL);
        hTime.setField(frame, "epochPicos", xInt128.INSTANCE.makeHandle(llNow));
        hTime.setField(frame, "timezone", timezone(frame));
        hTime.makeImmutable();

        return hTime;
        }

    protected GenericHandle timezone(Frame frame)
        {
        GenericHandle hTimeZone = m_hTimeZone;
        if (hTimeZone == null)
            {
            ConstantPool    pool           = pool();
            ClassStructure  structTimeZone = f_container.getClassStructure("temporal.TimeZone");
            TypeConstant    typeTimeZone   = structTimeZone.getCanonicalType();
            TypeComposition clzTimeZone    = typeTimeZone.ensureClass(frame);

            ClassStructure  structRule     = (ClassStructure) structTimeZone.getChild("Rule");
            TypeConstant    typeRule       = structRule.getCanonicalType();
            TypeConstant    typeRuleArray  = pool.ensureArrayType(typeRule);
            TypeComposition clzRuleArray   = typeRuleArray.ensureClass(frame);

            m_hTimeZone = hTimeZone = new GenericHandle(clzTimeZone);

            long lOffset = 0; // TODO CP
            hTimeZone.setField(frame, "picos", xInt64.makeHandle(lOffset));
            hTimeZone.setField(frame, "name",  xNullable.NULL);
            hTimeZone.setField(frame, "rules", xArray.createEmptyArray(clzRuleArray, 0, Mutability.Mutable));
            hTimeZone.makeImmutable();
            }

        return hTimeZone;
        }

    protected TypeComposition ensureTimeClass()
        {
        TypeComposition clz = m_clzTime;
        if (clz == null)
            {
            clz = m_clzTime =
                f_container.getTemplate("temporal.Time").getCanonicalClass();
            }
        return clz;
        }

    protected static class Alarm
            extends TimerTask
        {
        /**
         * Construct an alarm.
         *
         * @param refFunction  the weak ref to a function to call when the alarm triggers
         */
        protected Alarm(WeakCallback refFunction)
            {
            f_refCallback = refFunction;

            refFunction.get().registerNotification();
            }

        @Override
        public void run()
            {
            ServiceContext context = f_refCallback.get();
            if (context != null)
                {
                WeakCallback.Callback callback = f_refCallback.getCallback();
                context.callLater(callback.frame(), callback.functionHandle(), Utils.OBJECTS_NONE);
                context.unregisterNotification();
                }
            }

        @Override
        public boolean cancel()
            {
            boolean        fCancelled = super.cancel();
            ServiceContext context    = f_refCallback.get();
            if (context != null)
                {
                context.unregisterNotification();
                }
            return fCancelled;
            }

        private final WeakCallback f_refCallback;
        }


    // ----- constants and fields ------------------------------------------------------------------

    public static Timer TIMER = new Timer("ecstasy:LocalClock", true);

    /**
     * Cached Time class.
     */
    private TypeComposition m_clzTime;

    /**
     * Cached TimeZone handle.
     */
    private GenericHandle m_hTimeZone;

    /**
     * Cached LocalClock handle.
     */
    private ObjectHandle m_hLocalClock;
    }