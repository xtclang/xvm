package org.xvm.runtime.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

public class xUInt128
        extends xBaseInt128
    {
    public xUInt128(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();
        }

    /**
     * Converts an object of "this" integer type to the type represented by the template.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    protected int convertIntegerType(Frame frame, xConstrainedInteger template,
                                     ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();

        if (ll.getHighValue() != 0)
            {
            return overflow(frame);
            }

        long lVal = ll.getLowValue();

        // UInt64 fits always
        if (!(template instanceof xUInt64) &&
                (lVal < 0 || lVal > template.f_cMaxValue))
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, template.makeJavaLong(lVal));
        }
    }
