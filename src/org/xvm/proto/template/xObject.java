package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;

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
    public static MethodConstant TO_STRING; // TODO: should be MethodIdConst

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
        TO_STRING = f_types.f_adapter.getMethod("Object", "to", VOID, STRING).getIdentityConstant();
        markNativeMethod("to", VOID, STRING);
        }
    }
