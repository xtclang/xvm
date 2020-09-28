package org.xvm.runtime.template._native.temporal;


import java.util.Timer;
import java.util.TimerTask;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.GenericArrayHandle;

import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
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
    public static Timer TIMER = new Timer("ecstasy:LocalClock", true);

    public xLocalClock(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeProperty("now");
        markNativeProperty("timezone");

        markNativeMethod("schedule", new String[]{"temporal.DateTime", "temporal.Clock.Alarm"}, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "now":
                return frame.assignValue(iReturn, dateTimeNow());

            case "timezone":
                return frame.assignValue(iReturn, timezone());
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


    // -----  helpers ------------------------------------------------------------------------------

    protected GenericHandle dateTimeNow()
        {
        TypeComposition clzDateTime = ensureDateTimeClass();
        GenericHandle hDateTime = new GenericHandle(clzDateTime);

        LongLong llNow = new LongLong(System.currentTimeMillis()).mul(PICOS_PER_MILLI_LL);
        hDateTime.setField("epochPicos", xUInt128.INSTANCE.makeLongLong(llNow));
        hDateTime.setField("timezone", timezone());
        hDateTime.makeImmutable();

        return hDateTime;
        }

    protected GenericHandle timezone()
        {
        GenericHandle hTimeZone = m_hTimeZone;
        if (hTimeZone == null)
            {
            ConstantPool    pool           = pool();
            ClassStructure  structTimeZone = f_templates.getClassStructure("temporal.TimeZone");
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
            hTimeZone.setField("rules", new GenericArrayHandle(
                    clzRuleArray, Utils.OBJECTS_NONE, xArray.Mutability.Mutable));
            hTimeZone.makeImmutable();
            }

        return hTimeZone;
        }

    protected TypeComposition ensureDateTimeClass()
        {
        TypeComposition clz = m_clzDateTime;
        if (clz == null)
            {
            clz = m_clzDateTime =
                f_templates.getTemplate("temporal.DateTime").getCanonicalClass();
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
            LongLongHandle llEpoch = (LongLongHandle) hWakeup.getField("epochPicos");

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
            f_context.callLater(f_hFunction, Utils.OBJECTS_NONE, true);
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

    protected static final long     PICOS_PER_MILLI    = xNanosTimer.PICOS_PER_MILLI;
    protected static final LongLong PICOS_PER_MILLI_LL = xNanosTimer.PICOS_PER_MILLI_LL;

    /**
     * Cached DateTime class.
     */
    private TypeComposition m_clzDateTime;

    /**
     * Cached TimeZone handle.
     */
    private GenericHandle m_hTimeZone;
    }
