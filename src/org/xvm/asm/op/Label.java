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
        }
    }
