package org.xvm.asm.op;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.compiler.ast.Context;


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

    /**
     * Save off the original register to be restored when this label is reached,
     *
     * @param sName  the register name
     * @param reg    the register
     */
    public void addRestore(String sName, Register reg)
        {
        Map<String, Register> map = m_mapRestore;
        if (map == null)
            {
            m_mapRestore = map = new HashMap<>();
            }
        map.putIfAbsent(sName, reg);
        }

    /**
     * Restore the original registers at the specified context.
     *
     * @param context  the current compilation context
     */
    public void restoreNarrowed(Context context)
        {
        Map<String, Register> map = m_mapRestore;
        if (map != null)
            {
            for (Map.Entry<String, Register> entry : map.entrySet())
                {
                context.restoreArgument(entry.getKey(), entry.getValue());
                }
            }
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(m_sName)
          .append(": ");

        Op op = getNextOp();
        if (op != null)
            {
            sb.append(op);
            }

        return sb.toString();
        }

    /**
     * A counter to give labels different names while debugging. Race conditions are not a concern.
     */
    static int cLabels;

    /**
     * A name of the label, which is typically auto-generated. This is only for debugging; it is
     * discarded on assembly, like the label itself.
     */
    transient String m_sName;

    /**
     * Saved off registers to be restored when the label is reached.
     */
    transient Map<String, Register> m_mapRestore;
    }