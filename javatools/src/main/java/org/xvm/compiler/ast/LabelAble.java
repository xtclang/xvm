package org.xvm.compiler.ast;


import org.xvm.asm.Register;


/**
 * An interface implemented by statements that can expose variables via a label.
 */
public interface LabelAble
    {
    /**
     * Determine if the statement exposes the specified variable name via its label.
     *
     * @param sName  the variable name, for example "first" or "count"
     *
     * @return true iff the statement exposes the specified variable via its label
     */
    boolean hasLabelVar(String sName);

    /**
     * Obtain the register for the variable of the specified name which is exposed via a label.
     *
     * @param sName  the variable name
     *
     * @return the corresponding register
     */
    Register getLabelVar(String sName);
    }