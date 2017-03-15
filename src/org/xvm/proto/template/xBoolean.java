package org.xvm.proto.template;

import org.xvm.proto.*;

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
        BooleanHandle hThis = (BooleanHandle) handle;

        hThis.m_fValue = ((Boolean) oValue).booleanValue();
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new BooleanHandle(clazz.ensurePublicType(), clazz);
        }

    public static class BooleanHandle
            extends ObjectHandle
        {
        protected boolean m_fValue;

        protected BooleanHandle(Type type, TypeComposition clazz)
            {
            super(clazz, type);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_fValue;
            }
        }
    }
