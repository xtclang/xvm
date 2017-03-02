package org.xvm.proto.template;

import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xBit
        extends xObject
    {
    public xBit(TypeSet types)
        {
        super(types, "x:Bit", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        //    construct Bit(IntLiteral literal)     // TODO
        //
        //    private IntLiteral literal;
        //    static Bit defaultValue = 0;        // TODO
        //    IntLiteral to<IntLiteral>()
        //    Boolean to<Boolean>()
        //    @auto Byte to<Byte>()
        //    @auto Int to<Int>()
        //    @auto UInt to<UInt>()
        //    @op Bit and(Bit that)
        //    @op Bit or(Bit that)
        //    @op Bit xor(Bit that)
        //    @op Bit not()

        PropertyTemplate ptLiteral = addPropertyTemplate("literal", "x:IntLiteral");
        ptLiteral.setGetAccess(Access.Private);
        ptLiteral.setSetAccess(Access.Private);

        addMethodTemplate("to", new String[]{"x:IntLiteral"}, new String[]{"x:IntLiteral"});
        addMethodTemplate("to", BOOLEAN, BOOLEAN);
        addMethodTemplate("to", new String[]{"x:Byte"}, new String[]{"x:Byte"});
        addMethodTemplate("to", INT, INT);
        addMethodTemplate("to", new String[] {"x:UInt64"}, new String[] {"x:UInt64"});

        addMethodTemplate("and", THIS, THIS);
        addMethodTemplate("or",  THIS, THIS);
        addMethodTemplate("xor", THIS, THIS);
        addMethodTemplate("not", VOID, THIS);
        }
    }
