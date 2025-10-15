package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.Ctx.CtorCtx;

import org.xtclang.ecstasy.text.String;

/**
 * Native implementation for `ecstasy.Exception`.
 */
public class Exception extends xConst {
    public Exception(Ctx ctx) {
        super(ctx);
    }

    // natural property type: String?
    public xObj text;
    // natural property type: Exception?
    public xObj cause;
    // the associated native Java exception
    public xException $exception;

    /**
     * This is a static method that will be called by the naturally constructed sub-classes.
     * See {@link org.xvm.javajit.builders.CommonBuilder#assembleNew}.
     *
     * The name is reserved at {@link org.xvm.javajit.NativeNames}
     */
    public static void construct$n(Ctx ctx, CtorCtx cctx, Exception thi$, xObj message, xObj cause) {
        thi$.text       = message instanceof String text ? text : Nullable.Null;
        thi$.cause      = cause instanceof Exception e ? e : Nullable.Null;
        thi$.$exception = thi$.$createJavaException(cause instanceof Exception e ? e.$exception : null);
    }

    /**
     * Helper method for native exception construction.
     */
    public xException $init(Ctx ctx, java.lang.String message, Throwable cause) {
        this.text       = message == null ? String.of(ctx, message) : Nullable.Null;
        this.cause      = cause instanceof xException e ? e.exception : Nullable.Null;
        this.$exception = $createJavaException(cause);
        return $exception;
    }

    /**
     * This method will be overridden by each subclass to instantiate a corresponding Java exception.
     */
    public xException $createJavaException(Throwable cause) {
        return new xException(cause, this);
    }

    @Override
    public String toString(Ctx ctx) {
        return String.of(ctx, toString());
    }

    @Override
    public java.lang.String toString() {
        java.lang.String className = getClass().getName();
        if (className.startsWith("org.xtclang.ecstasy.")) {
            className = className.substring("org.xtclang.ecstasy.".length());
        }

        // TODO: replace with ecstasy.StringBuffer and move to "toString(Ctx ctx)"
        StringBuilder sb = new StringBuilder(className);
        sb.append(": ")
          .append(text == Nullable.Null ? "" : text.toString($ctx()));
        for (StackTraceElement el : $exception.getStackTrace()) {
            if (el.getFileName().endsWith(".x") && !el.getMethodName().startsWith("$")) {
                sb.append("\n    at ")
                  .append(el);
            }
        }
        return sb.toString();
    }

    public static xException $ro(Ctx ctx, java.lang.String text) {
        return new ReadOnly(ctx).$init(ctx, text, null);
    }

    public static xException $oob(Ctx ctx, java.lang.String text) {
        return new OutOfBounds(ctx).$init(ctx, text, null);
    }

    public static xException $unsupported(Ctx ctx, java.lang.String text) {
        return new Unsupported(ctx).$init(ctx, text, null);
    }
}
