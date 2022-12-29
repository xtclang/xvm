package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;

import org.xvm.runtime.template.xConst;


/**
 * Native Number support.
 */
public class xNumber
        extends xConst
    {
    public xNumber(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        String sName = f_sName;

        markNativeMethod("toInt"   , null, sName.equals("numbers.Int")    ? THIS : new String[]{"numbers.Int"});
        markNativeMethod("toInt8"  , null, sName.equals("numbers.Int8")   ? THIS : new String[]{"numbers.Int8"});
        markNativeMethod("toInt16" , null, sName.equals("numbers.Int16")  ? THIS : new String[]{"numbers.Int16"});
        markNativeMethod("toInt32" , null, sName.equals("numbers.Int32")  ? THIS : new String[]{"numbers.Int32"});
        markNativeMethod("toInt64" , null, sName.equals("numbers.Int64")  ? THIS : new String[]{"numbers.Int64"});
        markNativeMethod("toUInt"  , null, sName.equals("numbers.UInt")   ? THIS : new String[]{"numbers.UInt"});
        markNativeMethod("toUInt8" , null, sName.equals("numbers.UInt8")  ? THIS : new String[]{"numbers.UInt8"});
        markNativeMethod("toUInt16", null, sName.equals("numbers.UInt16") ? THIS : new String[]{"numbers.UInt16"});
        markNativeMethod("toUInt32", null, sName.equals("numbers.UInt32") ? THIS : new String[]{"numbers.UInt32"});
        markNativeMethod("toUInt64", null, sName.equals("numbers.UInt64") ? THIS : new String[]{"numbers.UInt64"});

        markNativeMethod("toFloat16"     , null, new String[]{"numbers.Float16"});
        markNativeMethod("toFloat32"     , null, new String[]{"numbers.Float32"});
        markNativeMethod("toFloat64"     , null, new String[]{"numbers.Float64"});

        markNativeMethod("toInt128"      , null, new String[]{"numbers.Int128"});
        markNativeMethod("toUInt128"     , null, new String[]{"numbers.UInt128"});
        markNativeMethod("toIntN"        , null, new String[]{"numbers.IntN"});
        markNativeMethod("toUIntN"       , null, new String[]{"numbers.UIntN"});
        markNativeMethod("toFloatN"      , null, new String[]{"numbers.FloatN"});
        markNativeMethod("toDecN"        , null, new String[]{"numbers.DecN"});
        }
    }