package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;
import org.xvm.proto.Utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xObject
        extends ClassTemplate
    {
    public static xObject INSTANCE;
    public static TypeComposition CLASS;
    private final static Map<Integer, Type[]> s_mapCanonical = new HashMap<>(4);

    public xObject(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            CLASS = f_clazzCanonical;
            }
        }

    @Override
    public void initDeclared()
        {
        if (f_struct.getFormat() == Component.Format.CLASS)
            {
            markNativeMethod("to", VOID);
            }
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodStructure method, ObjectHandle[] ahArg, int iReturn)
        {
        switch (ahArg.length)
            {
            case 0:
                if (method.getName().equals("to"))
                    {
                    // how to differentiate; check the method's return type?
                    return frame.assignValue(iReturn, xString.makeHandle(hTarget.toString()));
                    }
            }
        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
        }

    public static Type[] getTypeArray(int c)
        {
        return s_mapCanonical.computeIfAbsent(c, x ->
            {
            Type[] aType = new Type[c];
            if (c > 0)
                {
                Arrays.fill(aType, CLASS.ensurePublicType());
                }
            return aType;
            });
        }
    }
