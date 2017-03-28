package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xOrdered
        extends xObject
    {
    public xOrdered(TypeSet types)
        {
        super(types, "x:Ordered", "x:Object", Shape.Enum);
        }

    // subclassing
    protected xOrdered(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
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
        addMethodTemplate("to", new String[]{"x:Int"}, new String[]{"x:Int"});
        addMethodTemplate("to", new String[]{"x:UInt"}, new String[]{"x:UInt"});

        addMethodTemplate("and", BOOLEAN, BOOLEAN);
        addMethodTemplate("or", BOOLEAN, BOOLEAN);
        addMethodTemplate("xor", BOOLEAN, BOOLEAN);
        addMethodTemplate("not", VOID, BOOLEAN);
        }
    }
