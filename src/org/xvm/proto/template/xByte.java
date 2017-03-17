package org.xvm.proto.template;

import org.xvm.proto.ObjectHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xByte
        extends xObject
    {
    public xByte(TypeSet types)
        {
        super(types, "x:Byte", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        //    construct Bit(IntLiteral literal)     // TODO
        //
        //    private IntLiteral literal;
        //    static Byte defaultValue = 0;        // TODO
        //    IntLiteral to<IntLiteral>()
        //    Boolean to<Boolean>()
        //    @auto Bit to<Bit>()
        //    @auto Int to<Int>()
        //    @auto UInt to<UInt>()
        //    @op Byte and(Byte that)
        //    @op Byte or(Byte that)
        //    @op Byte xor(Byte that)
        //    @op Byte not()

        PropertyTemplate ptLiteral = addPropertyTemplate("literal", "x:IntLiteral");
        ptLiteral.setGetAccess(Access.Private);
        ptLiteral.setSetAccess(Access.Private);

        addMethodTemplate("to", new String[]{"x:IntLiteral"}, new String[]{"x:IntLiteral"});
        addMethodTemplate("to", BOOLEAN, BOOLEAN);
        addMethodTemplate("to", new String[]{"x:Bit"}, new String[]{"x:Bit"});
        addMethodTemplate("to", INT, INT);
        // addMethodTemplate("to", new String[] {"x:UInt64"}, new String[] {"x:UInt64"});

        addMethodTemplate("and", THIS, THIS);
        addMethodTemplate("or",  THIS, THIS);
        addMethodTemplate("xor", THIS, THIS);
        addMethodTemplate("not", VOID, THIS);
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Object oValue)
        {
        ObjectHandle.JavaLong hThis = (ObjectHandle.JavaLong) handle;

        hThis.m_lValue = ((Byte) oValue).longValue();
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new ObjectHandle.JavaLong(clazz);
        }
    }
