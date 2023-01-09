package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native IntNumber support.
 */
public abstract class xIntNumber
        extends xNumber
    {
    public xIntNumber(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeMethod("toChar", null, new String[]{"text.Char"});

        markNativeProperty("bitCount");
        markNativeProperty("bitLength");
        markNativeProperty("leftmostBit");
        markNativeProperty("rightmostBit");
        markNativeProperty("trailingZeroCount");

        markNativeMethod("shiftLeft" , INT, THIS);
        markNativeMethod("shiftRight", INT, THIS);

        super.initNative();
        }
    }