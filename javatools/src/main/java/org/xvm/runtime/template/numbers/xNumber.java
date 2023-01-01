package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;

import org.xvm.runtime.template.xConst;


/**
 * Native Number support.
 */
public abstract class xNumber
        extends xConst
    {
    public xNumber(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        String[] NAMES =
                {
                "Int",  "Int8",  "Int16",  "Int32",  "Int64",  "Int128",  "IntN",
                "UInt", "UInt8", "UInt16", "UInt32", "UInt64", "UInt128", "UIntN",

                "Float16", "Float32", "Float64", "FloatN",

                "Dec32", "Dec64", "Dec128", "DecN"
                };

        for (String sName : NAMES)
            {
            String sNameQ = "numbers." + sName;
            if (!sNameQ.equals(f_sName))
                {
                markNativeMethod("to" + sName, null, new String[]{sNameQ});
                }
            }

        markNativeProperty("bits");
        }
    }