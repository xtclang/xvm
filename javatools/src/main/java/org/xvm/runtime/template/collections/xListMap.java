package org.xvm.runtime.template.collections;


import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MapConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredArrayHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;


/**
 * Native ListMap support.
 */
public class xListMap
        extends ClassTemplate
    {
    public static xListMap INSTANCE;

    public xListMap(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        CONSTRUCTOR = getStructure().findMethod("construct", 2);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MapConstant)
            {
            TypeConstant            typeMap   = constant.getType();
            Map<Constant, Constant> mapValues = ((MapConstant) constant).getValue();
            int                     cEntries  = mapValues.size();

            ObjectHandle[] ahKey        = new ObjectHandle[cEntries];
            ObjectHandle[] ahVal        = new ObjectHandle[cEntries];
            boolean        fDeferredKey = false;
            boolean        fDeferredVal = false;
            int            ix           = 0;
            for (Map.Entry<Constant, Constant> entry : mapValues.entrySet())
                {
                ObjectHandle hKey = frame.getConstHandle(entry.getKey());
                ObjectHandle hVal = frame.getConstHandle(entry.getValue());

                fDeferredKey |= Op.isDeferred(hKey);
                fDeferredVal |= Op.isDeferred(hVal);

                ahKey[ix] = hKey;
                ahVal[ix] = hVal;
                ix++;
                }

            return constructMap(frame, typeMap, ahKey, ahVal, fDeferredKey, fDeferredVal, Op.A_STACK);
            }
        return super.createConstHandle(frame, constant);
        }

    /**
     * Create an immutable ListMap for a specified type and content.
     *
     * @param frame         the current frame
     * @param typeMap       the map type
     * @param ahKey         the array of keys
     * @param ahKey         the array of values
     * @param fDeferredKey  if true, some key handles could be deferred
     * @param fDeferredVal  if true, some value handles could be deferred
     * @param iReturn       the register to place the resulting map to
     *
     * @return R_CALL or R_EXCEPTION
     */
    public int constructMap(Frame frame, TypeConstant typeMap, ObjectHandle[] ahKey, ObjectHandle[] ahVal,
                               boolean fDeferredKey, boolean fDeferredVal, int iReturn)
        {
        ConstantPool pool         = frame.poolContext();
        TypeConstant typeKey      = typeMap.resolveGenericType("Key");
        TypeConstant typeVal      = typeMap.resolveGenericType("Value");
        TypeConstant typeKeyArray = pool.ensureArrayType(typeKey);
        TypeConstant typeValArray = pool.ensureArrayType(typeVal);

        TypeComposition clzKeyArray = f_templates.resolveClass(typeKeyArray);
        TypeComposition clzValArray = f_templates.resolveClass(typeValArray);
        TypeComposition clzMap      = ensureClass(
            pool.ensureParameterizedTypeConstant(getClassConstant().getType(), typeKey, typeVal));

        ObjectHandle     haKeys = fDeferredKey
                ? new DeferredArrayHandle(clzKeyArray, ahKey)
                : ((xArray) clzKeyArray.getTemplate()).createArrayHandle(clzKeyArray, ahKey);
        ObjectHandle     haVals = fDeferredVal
                ? new DeferredArrayHandle(clzValArray, ahVal)
                : ((xArray) clzValArray.getTemplate()).createArrayHandle(clzValArray, ahVal);

        ObjectHandle[] ahArg = new ObjectHandle[CONSTRUCTOR.getMaxVars()];
        ahArg[0] = haKeys;
        ahArg[1] = haVals;

        if (fDeferredKey || fDeferredVal)
            {
            Frame.Continuation stepNext = frameCaller ->
                construct(frameCaller, CONSTRUCTOR, clzMap, null, ahArg, iReturn);

            return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

        return construct(frame, CONSTRUCTOR, clzMap, null, ahArg, iReturn);
        }


    private static MethodStructure CONSTRUCTOR;
    }
