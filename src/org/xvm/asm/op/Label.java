package org.xvm.asm.op;


import org.xvm.asm.Op;


/**
 * A label is an op that can be created as a forward reference for a branching construct. It does
 * not actually exist in the opcode stream, in that it does not generate any bytes.
 */
public class Label
        extends Op.Prefix
    {
    /**
     * Construct a label op.
     */
    public Label()
        {
        this(String.valueOf(++cLabels));
        if (cLabels >= 9999)
            {
            cLabels = 0;
            }
        }

    /**
     * Construct a label op with a name for the label (useful for debugging).
     *
     * @param sName the label name
     */
    public Label(String sName)
        {
        m_sName = sName;
        }

    /**
     * @return the name of the label, for debugging purposes
     */
    public String getName()
        {
        return m_sName;
        }

    @Override
    public String toString()
        {
        return m_sName + ":";
        }

    /**
     * A counter to give labels different names while debugging. Race conditions are not a concern.
     */
    static int cLabels;

    /**
     * A name of the label, which is typically auto-generated. This is only for debugging; it is
     * discarded on assembly, like the label itself.
     */
    String m_sName;
    }
