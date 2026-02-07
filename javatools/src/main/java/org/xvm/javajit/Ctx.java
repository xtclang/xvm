package org.xvm.javajit;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.function.Function;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.xvm.javajit.Builder.CD_JavaObject;
import static org.xvm.javajit.Builder.CD_JavaString;
import static org.xvm.javajit.Builder.CD_TypeConstant;

/**
 * The runtime context of a logical thread of execution. Enables multiple returns, tuple
 * return functionality, etc.
 *
 * Ideally, this struct will fit inside two cache lines, with the hot fields in the first line.
 */
public final class Ctx {
    // ----- constructors --------------------------------------------------------------------------

    public Ctx(Xvm xvm, Container container) {
        this.xvm = xvm;
        this.container = container;
    }

    // ----- fields --------------------------------------------------------------------------------

    public final Xvm xvm;
    public final Container container;
    // xSvc service;
    // etc.
    // public int depth;    // call depth
    // public int ra;
    // public int ca;

    // fields to hold additional return values
    public long     i0; // hottest at top
    public long     i1;
    public Object   o0; // unpredictable reference byte size, so double refs up for 64-bit alignment
    public Object   o1;
    public long     i2;
    public long     i3;
    public Object   o2;
    public Object   o3;
    public long     i4;
    public long     i5;
    public Object   o4;
    public Object   o5;
    public long     i6;
    public long     i7;
    public Object   o6;
    public Object   o7;
    public long[]   iN;
    public Object[] oN; // coldest at the bottom (no fields should be declared below this point!)

    // ----- memory accounting ---------------------------------------------------------------------

    public static final ScopedValue<Ctx> Current = ScopedValue.newInstance();

    /**
     * Obtain the current xvm context. This method should only be called from code running on an
     * xvm fiber.
     *
     * @return the Ctx for the current fiber
     */
    public static Ctx get() {
        return Current.get();
    }

    // ----- memory accounting ---------------------------------------------------------------------

    /**
     * "Request" the specified number of bytes. This call may return immediately, wait indefinitely
     * (i.e. park this virtual thread and schedule a different fiber to run), or even kill the
     * current container.
     *
     * @param size  the size (in bytes) of memory desired
     */
    public void alloc(long size) {
        // TODO
    }

    /**
     * Report that the specified number of bytes was already taken without requesting it first. This
     * call should return immediately, but in extreme situations this call may wait indefinitely
     * (i.e. park this virtual thread and schedule a different fiber to run), or even kill the
     * current container.
     *
     * @param size  the size (in bytes) of memory that was taken without asking
     */
    public void allocated(long size) {
        // TODO
    }

    /**
     * Used when a copy is being made, such that some old memory is still (temporarily) required
     * to perform the copy, while the new memory (which could be larger or smaller) needs to be
     * allocated. Like {@link #alloc(long)}, this call may return immediately, wait
     * indefinitely (i.e. park this virtual thread and schedule a different fiber to run), or even
     * kill the current container. It is expected that the algorithm behind this differs slightly
     * from {@link #alloc(long)}, in that this method will permit the memory limits to be
     * exceeded on a temporary basis as long as the result does not imperil the health of the xvm.
     *
     * @param oldSize  the memory still being held onto temporarily that will be freed in a moment
     * @param newSize  the new memory being requested (the full amount, not just the delta amount)
     */
    public void realloc(long oldSize, long newSize) {
        // TODO
    }

    /**
     * Indicate that the specified number of bytes is no longer being held. This call can be
     * expected to return immediately.
     *
     * @param size  the size (in bytes) of memory to assume has been freed
     */
    public void free(long size) {
        // TODO
    }

    // ----- Constructor support -------------------------------------------------------------------

    public CtorCtx ctorCtx() {
        return new CtorCtx();
    }

    /**
     * The constructor context is required if a “finally” chain exists.
     */
    public static class CtorCtx {
        // TODO CP:
    }

    // ----- Container and Service support ---------------------------------------------------------

    // TODO


    /**
     * Helper method to retrieve a constant at the specified index.
     */
    public Constant getConstant(int index) {
        return container.typeSystem.getConstant(index);
    }

    /**
     * Injection helper.
     */
    public Object inject(TypeConstant resourceType, String resourceName, Object opts) {
        var supplier = container.injector.supplierOf(resourceType, resourceName);
        return supplier != null ? supplier.apply(opts) : null;
    }

    // ----- debugging support ---------------------------------------------------------------------

    public void log(java.lang.String message) {
        System.err.println(message);
    }

    // ----- Ctx method descriptors ----------------------------------------------------------------

    public static final MethodTypeDesc MD_log = MethodTypeDesc.of(CD_void, CD_JavaString);

    public static final MethodTypeDesc MD_getConstant = MethodTypeDesc.of(
        ClassDesc.of(Constant.class.getName()), CD_int);

    public static MethodTypeDesc MD_inject = MethodTypeDesc.of(
        CD_JavaObject, CD_TypeConstant, CD_JavaString, CD_JavaObject);
}
