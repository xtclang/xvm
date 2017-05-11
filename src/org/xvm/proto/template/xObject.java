package org.xvm.proto.template;

import org.xvm.asm.Constants;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
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
        extends TypeCompositionTemplate
    {
    public static xObject INSTANCE;
    public static TypeComposition CLASS;
    private final static Map<Integer, Type[]> s_mapCanonical = new HashMap<>(4);

    public xObject(TypeSet types)
        {
        super(types, "x:Object", null, Shape.Class);

        INSTANCE = this;
        CLASS = resolve(Utils.TYPE_NONE);
        }

    // subclassing
    protected xObject(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        // protected Meta meta.get()
        // static Boolean equals(Object o1, Object o2)
        // String to<String>()
        // Object[] to<Object[]>()
        // (Object) to<(Object)>()
        // @auto function Object() to<function Object()>()

        PropertyTemplate propMeta = ensurePropertyTemplate("meta", "x:Meta");
        propMeta.setGetAccess(Constants.Access.PROTECTED);
        propMeta.setSetAccess(Constants.Access.PROTECTED);

        ensureFunctionTemplate("equals", new String[]{"x:Object", "x:Object"}, VOID);

        ensureMethodTemplate("to", VOID, STRING).markNative();
        ensureMethodTemplate("to", VOID, new String[]{"x:collections.Array<x:String>"});
        ensureMethodTemplate("to", VOID, new String[]{"x:Tuple<x:Object>"});
        ensureMethodTemplate("to", VOID, new String[]{"x:Function"});
        }

    @Override
    public ExceptionHandle invokeNative(Frame frame, ObjectHandle hTarget,
                                        MethodTemplate method, ObjectHandle[] ahArg, int iReturn)
        {
        switch (ahArg.length)
            {
            case 0:
                if (method.f_sName.equals("to"))
                    {
                    // how to differentiate; check the method's return type?
                    frame.assignValue(iReturn, xString.makeHandle(hTarget.toString()));
                    return null;
                    }
            }
        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
        }

    public static Type[] getTypeArray(int c)
        {
        return s_mapCanonical.computeIfAbsent(c, x ->
            {
            Type[] at = new Type[c];
            Arrays.fill(at, CLASS.ensurePublicType());
            return at;
            });
        }
    }
