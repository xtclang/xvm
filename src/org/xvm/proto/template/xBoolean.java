package org.xvm.proto.template;

import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xBoolean
        extends TypeCompositionTemplate
    {
    public xBoolean(TypeSet types)
        {
        super(types, "x:Boolean", "x:Object", Shape.Enum);
        }

    // subclassing
    protected xBoolean(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        if (!f_sName.equals("x:Boolean")) return; // avoid recursion

        // in-place declaration for True and False
        f_types.addTemplate(new xBoolean(f_types, "x:True", "x:Boolean", Shape.Enum));
        f_types.addTemplate(new xBoolean(f_types, "x:False", "x:Boolean", Shape.Enum));

        //    Bit  to<Bit>();
        //    Byte to<Byte>();
        //    Int  to<Int>();
        //    UInt to<UInt>();
        //
        //    @op Boolean and(Boolean that);
        //    @op Boolean or(Boolean that);
        //    @op Boolean xor(Boolean that);
        //    @op Boolean not();

        addMethodTemplate("to", new String[]{"x:Bit"}, new String[]{"x:Bit"});
        addMethodTemplate("to", new String[]{"x:Byte"}, new String[]{"x:Byte"});
        addMethodTemplate("to", INT, INT);
        // addMethodTemplate("to", new String[]{"x:UInt64"}, new String[]{"x:UInt64"});

        addMethodTemplate("and", THIS, THIS);
        addMethodTemplate("or",  THIS, THIS);
        addMethodTemplate("xor", THIS, THIS);
        addMethodTemplate("not", VOID, THIS);
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Object oValue)
        {
        JavaLong hThis = (JavaLong) handle;

        hThis.m_lValue = ((Boolean) oValue).booleanValue() ? 1 : 0;
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new JavaLong(clazz);
        }
    }
