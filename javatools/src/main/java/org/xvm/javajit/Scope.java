package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.util.HashMap;
import java.util.Map;

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
        this.depth      = parent.depth + 1;
    }

    private final BuildContext bctx;
    private final CodeBuilder code;

    /**
     * The parent Scope.
     */
    public Scope parent;

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
     * The depth of this scope.
     */
    public int depth;

    /**
     * A map of synthetic variables declared in this scope.
     */
    public Map<String, Integer> synthetics;

    /**
     * Enter a new Scope.
     */
    public Scope enter() {
        return new Scope(this);
    }

    /**
     * Allocate Java slot(s) for an XVM variable of the specified kind.
     *
     * @return the Java slot for the newly allocated local variable
     */
    public int allocateLocal(int varIndex, TypeKind kind) {
        int slot = allocateJavaSlot(kind);
        assert parent == null || varIndex > parent.topVar;
        topVar = Math.max(topVar, varIndex);
        return slot;
    }

    /**
     * Allocate Java slot(s) for a synthetic variable of the specified name and kind.
     *
     * @return the Java slot for the newly allocated synthetic variable
     */
    public int allocateSynthetic(String name, TypeKind kind) {
        int slot = allocateJavaSlot(kind);
        if (synthetics == null) {
            synthetics = new HashMap<>();
        } else {
            assert !synthetics.containsKey(name);
        }
        synthetics.put(name, slot);
        return slot;
    }

    private int allocateJavaSlot(TypeKind kind) {
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
        return slot;
    }

    /**
     * @return a Java slot for the specified synthetic variable; -1 if not found
     */
    public int getSynthetic(String name) {
        Integer slot = synthetics.get(name);
        return slot == null ? -1 : slot.intValue();
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
