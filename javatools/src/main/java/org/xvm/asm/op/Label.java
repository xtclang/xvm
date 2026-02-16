package org.xvm.asm.op;


import java.lang.classfile.CodeBuilder;

import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.compiler.ast.Context;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.ActionChain;
import org.xvm.javajit.BuildContext.OpAction;


/**
 * A label is an op that can be created as a forward reference for a branching construct. It does
 * not actually exist in the opcode stream, in that it does not generate any bytes.
 */
public class Label
        extends Op.Prefix {
    /**
     * Construct a label op based on a unique counter value.
     */
    public Label(int counter) {
        this(String.valueOf(counter));
    }

    /**
     * Construct a label op with a name for the label (useful for debugging).
     *
     * @param sName the label name
     */
    public Label(String sName) {
        f_sName = sName;
    }

    /**
     * @return the name of the label, for debugging purposes
     */
    public String getName() {
        return f_sName;
    }

    /**
     * Save off the original register to be restored when this label is reached,
     *
     * @param sName  the register name
     * @param reg    the register
     */
    public void addRestore(String sName, Register reg) {
        Map<String, Register> map = m_mapRestore;
        if (map == null) {
            m_mapRestore = map = new HashMap<>();
        }
        map.putIfAbsent(sName, reg);
    }

    /**
     * Restore the original registers at the specified context.
     *
     * @param context  the current compilation context
     */
    public void restoreNarrowed(Context context) {
        Map<String, Register> map = m_mapRestore;
        if (map != null) {
            for (Map.Entry<String, Register> entry : map.entrySet()) {
                context.restoreArgument(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(f_sName)
          .append(": ");

        Op op = getNextOp();
        if (op != null) {
            sb.append(op);
        }

        return sb.toString();
    }

    // ----- JIT support ------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        getNextOp().computeTypes(bctx);
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        // there is a possibility that the label was only used by the "dead" code that has been
        // eliminated
        if (m_label != null) {
            code.labelBinding(m_label);
        }

        // it's important to call the action only after the label is set
        if (m_action != null) {
            int skipTo = m_action.prepare();
            if (skipTo != -1) {
                return skipTo;
            }
        }
        return getNextOp().build(bctx, code);
    }

    /**
     * Set a Java label associated with this Prefix.
     */
    public void setLabel(java.lang.classfile.Label label) {
        assert label != null && m_label == null;
        m_label = label;
    }

    /**
     * @return the associated label (if any)
     */
    public java.lang.classfile.Label getLabel() {
        return m_label;
    }

    /**
     * Set an action associated with this Prefix.
     */
    public void setAction(OpAction action) {
        if (m_action == null) {
            m_action = action;
        } else {
            m_action = new ActionChain(m_action, action);
        }
    }

    /**
     * Clear all JIT related state.
     */
    public void clear() {
        m_label  = null;
        m_action = null;
    }

    // ----- fields -----------------------------------------------------------------------

    /**
     * The label associated with this Prefix.
     */
    private java.lang.classfile.Label m_label;

    /**
     * The action associated with this Prefix.
     */
    private OpAction m_action;

    /**
     * A name of the label, which is typically auto-generated. This is only for debugging; it is
     * discarded on assembly, like the label itself.
     */
    transient final private String f_sName;

    /**
     * Saved off registers to be restored when the label is reached.
     */
    transient public Map<String, Register> m_mapRestore;
}