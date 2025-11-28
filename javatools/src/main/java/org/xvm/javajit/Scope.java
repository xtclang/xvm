package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.op.FinallyEnd;

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
        this.topReg     = 0;
        this.startAddr  = 0;

        startLabel = code.newLabel();
        endLabel   = code.newLabel();
    }

    /**
     * Construct the child scope.
     */
    private Scope(Scope parent, int startAddr) {
        this(parent.bctx, parent.code);

        this.parent     = parent;
        this.startLocal = parent.startLocal;
        this.topLocal   = parent.topLocal;
        this.topReg     = parent.topReg;
        this.depth      = parent.depth + 1;
        this.startAddr  = startAddr;
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
     * The top index of the Java stack for this scope (exclusive).
     */
    public int topLocal;

    /**
     * The top index of the XVM register for this scope (exclusive).
     */
    public int topReg;

    /**
     * The depth of this scope.
     */
    public int depth;

    /**
     * The address of the starting op for this scope. Used for debugging.
     */
    public int startAddr;

    /**
     * The list of jumps addresses the "finally" block may need to conditionally jump to.
     */
    public List<Integer> jumps;

    /**
     * A map of synthetic variables declared in this scope.
     */
    public Map<String, Integer> synthetics;

    /**
     * Enter a new Scope.
     *
     * @param startAddr  the address of the corresponding op
     */
    public Scope enter(int startAddr) {
        return new Scope(this, startAddr);
    }

    /**
     * Allocate Java slot(s) for an XVM register of the specified ClassDesc. Note, that there could
     * be multiple Java slots for the same XVM variable.
     *
     * @return the Java slot for the newly allocated local variable
     */
    public int allocateLocal(int regId, ClassDesc cd) {
        return allocateLocal(regId, Builder.toTypeKind(cd));
    }

    /**
     * Allocate Java slot(s) for an XVM register of the specified kind. Note, that there could be
     * multiple Java slots for the same XVM variable.
     *
     * @return the Java slot for the newly allocated local variable
     */
    public int allocateLocal(int regId, TypeKind kind) {
        int slot = allocateJavaSlot(kind);
        assert parent == null || regId >= parent.topReg;
        topReg = Math.max(topReg, regId + 1);
        return slot;
    }

    /**
     * Allocate a slot for an exception to be rethrown by the {@link FinallyEnd}.
     */
    public void allocateRethrow(CodeBuilder code) {
        // at the moment, the name doesn't have to be unique across the scopes since we don't
        // register the variable with the method, but we can always augment it by the "depth"
        int slot = allocateSynthetic("$rethrow", TypeKind.REFERENCE);
        code.aconst_null()
            .astore(slot);
    }

    /**
     * Retrieve the Java slot for an exception to be rethrown by the {@link FinallyEnd}.
     */
    public int getRethrow() {
        return getSynthetic("$rethrow", false);
    }

    /**
     * Allocate Java slots for conditional jumps by the {@link FinallyEnd}.
     */
    public void allocateJumps(CodeBuilder code, List<Integer> jumps) {
        for (Integer jump : jumps) {
            // $jumpN = false;
            int slot = allocateSynthetic("$jump" + jump, TypeKind.BOOLEAN);
            code.iconst_0()
                .istore(slot);
        }
        this.jumps = jumps;
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

    /**
     * @return a Java slot(s) of the specified class within this scope
     */
    public int allocateJavaSlot(ClassDesc cd) {
        return allocateJavaSlot(Builder.toTypeKind(cd));
    }

    /**
     * @return a Java slot(s) of the specified kind within this scope
     */
    public int allocateJavaSlot(TypeKind kind) {
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
    public int getSynthetic(String name, boolean recurse) {
        Integer slot = synthetics == null ? null : synthetics.get(name);
        return  slot == null
            ? recurse && parent != null
                ? parent.getSynthetic(name, true)
                : -1
            : slot.intValue();
    }

    /**
     * Exit this Scope.
     */
    public Scope exit() {
        if (parent == null) {
            throw new IllegalStateException();
        }
        if (parent.startLocal == -1) {
            // the top scope hasn't allocated anything; use this slot's start slot index
            parent.startLocal = this.startLocal;
            parent.topLocal   = this.topLocal;
        }
        code.labelBinding(endLabel);
        return parent;
    }
}
