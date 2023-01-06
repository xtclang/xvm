package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;


public class xUInt128
        extends BaseInt128
    {
    public static xUInt128 INSTANCE;

    public xUInt128(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected int convertToConstrainedType(Frame frame, xConstrainedInteger template,
                                           LongLong ll, boolean fTruncate, int iReturn)
        {
        long lVal = ll.getLowValue();
        if (!fTruncate)
            {
            if (ll.getHighValue() != 0)
                {
                return overflow(frame);
                }

            // UInt64 fits always
            if (!(template instanceof xUInt64) &&
                    (lVal < 0 || lVal > template.f_cMaxValue))
                {
                return overflow(frame);
                }
            }

        return frame.assignValue(iReturn, template.makeJavaLong(lVal));
        }
    }