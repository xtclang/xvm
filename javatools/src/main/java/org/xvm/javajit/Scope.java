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
        this.startLocal = -1;
        this.topLocal   = -1;
        this.topVar     = 0;

        startLabel = code.newLabel();
        endLabel   = code.newLabel();
    }

    /**
     * Construct the child scope.
     */
    private Scope(Scope parent) {
        this(parent.bctx, parent.code);

        this.parent     = parent;
        this.startLocal = parent.startLocal;
        this.topLocal   = parent.topLocal;
        this.topVar     = parent.topVar;
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
     * The top index of the XVM var for this scope.
     */
    public int topVar;

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
    public int allocateLocal(int varIndex, TypeKind kind) {
        int slot;
        if (topLocal >= bctx.maxLocal || startLocal == -1) {
            slot = code.allocateLocal(kind);
            if (startLocal == -1) {
                startLocal = slot;
            }
            bctx.maxLocal = topLocal = slot + kind.slotSize();
        } else { // topLocal < bctx.maxLocal
            slot      = topLocal;
            topLocal += kind.slotSize();
            if (topLocal > bctx.maxLocal) {
                bctx.maxLocal = code.allocateLocal(TypeKind.REFERENCE) + 1; // bump the code counter
                assert bctx.maxLocal == topLocal;
            }
        }
        assert parent == null || varIndex > parent.topVar;
        topVar = Math.max(topVar, varIndex);
        return slot;
    }

    /**
     * Exit this Scope.
     */
    public Scope exit() {
        if (parent == null) {
            throw new IllegalStateException();
        }
        if (parent.startLocal == -1) {
            parent.startLocal = parent.topLocal = startLocal;
        }
        code.labelBinding(endLabel);
        return parent;
    }
}
