package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

/**
 * Scope information for the JIT compiler.
 */
public class Scope {

    /**
     * Construct the initial scope.
     */
    public Scope(BuildContext bctx, CodeBuilder code) {
        this.bctx       = bctx;
        this.code       = code;
        this.startLocal = 0;
        this.topLocal   = 0;

        startLabel = code.newLabel();
        endLabel   = code.newLabel();
    }

    /**
     * Construct the child scope.
     */
    private Scope(Scope parent) {
        this(parent.bctx, parent.code);

        this.parent     = parent;
        this.startLocal = parent.topLocal;
        this.topLocal   = parent.topLocal;
    }

    private final BuildContext bctx;
    private final CodeBuilder code;

    /**
     * The parent Scope.
     */
    private Scope parent;

    /**
     * The start-of-scope label.
     */
    public final Label startLabel;

    /**
     * The end-of-scope label.
     */
    public final Label endLabel;

    /**
     * The start index of the Java stack for this scope.
     */
    public int startLocal;

    /**
     * The top index of the Java stack for this scope.
     */
    public int topLocal;

    /**
     * Enter a new Scope.
     */
    public Scope enter() {
        return new Scope(this);
    }

    /**
     * Allocate Java slot(s) for a variable of the specified kind.
     *
     * @return the Java slot for the newly allocated local variable
     */
    public int allocateLocal(TypeKind kind) {
        int slot;
        if (parent == null) {
            slot = code.allocateLocal(kind);
            bctx.maxLocal = topLocal = slot + kind.slotSize();
        } else {
            // for a child Scope we will update the code's locals upon the exit
            slot      = topLocal;
            topLocal += kind.slotSize();
        }
        return slot;
    }

    /**
     * Exit this Scope.
     */
    public Scope exit() {
        if (parent == null) {
            throw new IllegalStateException();
        }

        int extras = topLocal - parent.topLocal;
        if (extras > 0) {
            bctx.maxLocal = topLocal;
            while (extras-- > 0) {
                code.allocateLocal(TypeKind.REFERENCE); // single slot
            }
        }
        code.labelBinding(endLabel);
        return parent;
    }
}
