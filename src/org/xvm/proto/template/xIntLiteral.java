package org.xvm.proto.template;

import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xIntLiteral
        extends xObject
    {
    public xIntLiteral(TypeSet types)
        {
        super(types, "x:IntLiteral", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        //    construct IntLiteral(String text)     // TODO
        //
        //    Signum explicitSign = Zero;           // TODO
        //    Int radix = 10;
        //    VarUInt magnitude;
        //    Int minIntBits.get()
        //    @ro Int minUIntBits.get()
        //    @ro Int minFloatBits.get()
        //    @ro Int minDecBits.get()
        //    @auto Bit to<Bit>()
        //    @auto VarInt to<VarInt>();
        //    @auto Int8 to<Int8>()
        //    @auto Int16 to<Int16>();
        //    @auto Int32 to<Int32>();
        //    @auto Int64 to<Int64>();
        //    @auto Int128 to<Int128>();
        //    @auto VarUInt to<VarUInt>();
        //    @auto UInt8 to<UInt8>()
        //    @auto UInt16 to<UInt16>();
        //    @auto UInt32 to<UInt32>();
        //    @auto UInt64 to<UInt64>();
        //    @auto UInt128 to<UInt128>();
        //    @auto VarFloat to<VarFloat>();
        //    @auto Float16 to<Float16>()
        //    @auto Float32 to<Float32>()
        //    @auto Float64 to<Float64>()
        //    @auto Float128 to<Float128>()
        //    @auto VarDec to<VarDec>();
        //    @auto Dec32 to<Dec32>()
        //    @auto Dec64 to<Dec64>()
        //    @auto Dec128 to<Dec128>()

        ensurePropertyTemplate("radix", "x:Int");
        // TODO the rest

        ensureMethodTemplate("to", new String[]{"x:Bit"}, new String[]{"x:Bit"});
        // TODO conversions
        }
    }
