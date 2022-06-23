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

import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xUInt128;

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

        getCanonicalType().invalidateTypeInfo();
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

                Alarm          alarm   = new Alarm(frame.f_context, hAlarm);
                FunctionHandle hCancel = alarm.schedule(hWakeup);

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
                frame.f_context.f_container.createServiceContext("LocalClock"),
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

        LongLong llNow = new LongLong(System.currentTimeMillis()).mul(PICOS_PER_MILLI_LL);
        hTime.setField(frame, "epochPicos", xUInt128.INSTANCE.makeLongLong(llNow));
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

            long lOffset = 0; // TODO
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
        public Alarm(ServiceContext ctx, FunctionHandle hFunction)
            {
            f_context   = ctx;
            f_hFunction = hFunction;
            }

        protected FunctionHandle schedule(GenericHandle hWakeup)
            {
            // assert (hWakeup.timezone == NoTZ) == (this.timezone == NoTZ)
            LongLongHandle llEpoch = (LongLongHandle) hWakeup.getField(null, "epochPicos");

            long  ldtNow    = System.currentTimeMillis();
            long  ldtWakeup = llEpoch.getValue().divUnsigned(PICOS_PER_MILLI).getLowValue();
            long  cDelay    = Math.max(0, ldtWakeup - ldtNow);

            f_context.registerNotification();
            TIMER.schedule(this, cDelay);

            return new NativeFunctionHandle((_frame, _ah, _iReturn) ->
                {
                Alarm.this.cancel();
                return Op.R_NEXT;
                });
            }

        @Override
        public void run()
            {
            f_context.callLater(f_hFunction, Utils.OBJECTS_NONE);
            f_context.unregisterNotification();
            }

        @Override
        public boolean cancel()
            {
            if (super.cancel())
                {
                f_context.unregisterNotification();
                return true;
                }
            return false;
            }

        final private ServiceContext f_context;
        final private FunctionHandle f_hFunction;
        }


    // ----- constants and fields ------------------------------------------------------------------

    public static Timer TIMER = new Timer("ecstasy:LocalClock", true);

    protected static final long     PICOS_PER_MILLI    = xNanosTimer.PICOS_PER_MILLI;
    protected static final LongLong PICOS_PER_MILLI_LL = xNanosTimer.PICOS_PER_MILLI_LL;

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