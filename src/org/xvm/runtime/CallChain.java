package org.xvm.runtime;


import java.util.List;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;


/**
 * Represents a chain of invocation.
 */
public class CallChain
    {
    // an optimization for a single method chain;
    // also holds a "top"
    private final MethodStructure f_method;

    // an array of methods (only if m_method == null)
    private final MethodStructure[] f_aMethods;

    private final int f_cDepth;

    // a constructor for a single-method chain
    public CallChain(MethodStructure method)
        {
        f_method = method;
        f_aMethods = null;
        f_cDepth = 1;
        }

    // a generic constructor for a method chain
    public CallChain(List<MethodStructure> listMethods)
        {
        int cDepth = f_cDepth = listMethods.size();
        switch (cDepth)
            {
            case 0:
                f_aMethods = null;
                f_method = null;
                break;

            case 1:
                f_aMethods = null;
                f_method = listMethods.get(0);
                break;

            default:
                f_aMethods = listMethods.toArray(new MethodStructure[cDepth]);
                f_method = f_aMethods[0];
                break;
            }
        }

    public int getDepth()
        {
        return f_cDepth;
        }

    public MethodStructure getMethod(int nDepth)
        {
        return nDepth == 0       ? f_method :
               nDepth < f_cDepth ? f_aMethods[nDepth] : null;
        }

    public MethodStructure getTop()
        {
        return f_method;
        }

    public MethodStructure getSuper(Frame frame)
        {
        return getMethod(frame.m_nDepth + 1);
        }

    public boolean isNative()
        {
        MethodStructure method = f_method;
        if (method == null)
            {
            return isNativeProperty();
            }
        return Adapter.isNative(method);
        }

    protected boolean isNativeProperty()
        {
        throw new IllegalStateException();
        }

    public int invoke1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invoke1(this, 0, hTarget, ahVar, iReturn);
        }

    public int callSuper01(Frame frame, int iReturn)
        {
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodStructure methodSuper = getMethod(nDepth);
        if (methodSuper == null)
            {
            return getField(frame, hThis, iReturn);
            }

        if (Adapter.isNative(methodSuper))
            {
            return hThis.getTemplate().
                    invokeNativeN(frame, methodSuper, hThis, Utils.OBJECTS_NONE, iReturn);
            }

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.getMaxVars()];

        return frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
        }

    protected int getField(Frame frame, ObjectHandle hThis, int iReturn)
        {
        throw new IllegalStateException();
        }

    public int callSuper10(Frame frame, ObjectHandle hArg)
        {
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodStructure methodSuper = getMethod(nDepth);
        if (methodSuper == null)
            {
            return setField(frame, hThis, hArg);
            }

        if (Adapter.isNative(methodSuper))
            {
            return hThis.getTemplate().
                    invokeNative1(frame, methodSuper, hThis, hArg, Frame.RET_UNUSED);
            }

        ObjectHandle[] ahVar = new ObjectHandle[methodSuper.getMaxVars()];
        ahVar[1] = hArg;

        return frame.invoke1(this, nDepth, hThis, ahVar, Frame.RET_UNUSED);
        }

    protected int setField(Frame frame, ObjectHandle hThis, ObjectHandle hArg)
        {
        throw new IllegalStateException();
        }

    public int callSuperN1(Frame frame, ObjectHandle[] ahArg, int iReturn,
                           boolean fReturnTuple)
        {
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodStructure methodSuper = getMethod(nDepth);
        if (methodSuper == null)
            {
            throw new IllegalStateException();
            }

        if (Adapter.isNative(methodSuper))
            {
            return fReturnTuple
                ? hThis.getTemplate().
                    invokeNativeN(frame, methodSuper, hThis, ahArg, iReturn)
                : hThis.getTemplate().
                    invokeNativeT(frame, methodSuper, hThis, ahArg, iReturn);
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, methodSuper.getMaxVars());

        return fReturnTuple
            ? frame.invoke1(this, nDepth, hThis, ahVar, iReturn)
            : frame.invokeT(this, nDepth, hThis, ahVar, iReturn);
        }

    public int callSuperNN(Frame frame, ObjectHandle[] ahArg, int[] aiReturn)
        {
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodStructure methodSuper = getMethod(nDepth);
        if (methodSuper == null)
            {
            throw new IllegalStateException();
            }

        if (Adapter.isNative(methodSuper))
            {
            return hThis.getTemplate().
                    invokeNativeNN(frame, methodSuper, hThis, ahArg, aiReturn);
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, methodSuper.getMaxVars());

        return frame.invokeN(this, nDepth, hThis, ahVar, aiReturn);
        }

    // ----- PropertyCallChain -----

    public static class PropertyCallChain
            extends CallChain
        {
        // a property representing the field access
        private final PropertyStructure f_property;

        // get vs. set
        private final boolean f_fGet;

        // a constructor for a property access chain
        public PropertyCallChain(List<MethodStructure> listMethods, PropertyStructure property, boolean fGet)
            {
            super(listMethods);

            f_property = property;
            f_fGet = fGet;
            }

        public PropertyStructure getProperty()
            {
            return f_property;
            }

        @Override
        protected boolean isNativeProperty()
            {
            ClassTemplate.PropertyInfo info = f_property.getInfo();
            return info != null && (f_fGet ? info.m_fNativeGetter : info.m_fNativeSetter);
            }

        @Override
        protected int getField(Frame frame, ObjectHandle hThis, int iReturn)
            {
            return hThis.getTemplate().getFieldValue(frame, hThis, f_property, iReturn);
            }

        @Override
        protected int setField(Frame frame, ObjectHandle hThis, ObjectHandle hArg)
            {
            ObjectHandle.ExceptionHandle hException =
                    hThis.getTemplate().setFieldValue(hThis, f_property, hArg);
            return hException == null ? Op.R_NEXT : frame.raiseException(hException);
            }
        }
    }
