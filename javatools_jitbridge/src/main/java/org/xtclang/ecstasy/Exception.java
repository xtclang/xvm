package org.xtclang.ecstasy;

import java.util.Objects;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.Ctx.CtorCtx;

import org.xtclang.ecstasy.text.String;

/**
 * Native implementation for `ecstasy.Exception`.
 */
public class Exception extends nConst {
    public Exception(Ctx ctx) {
        super(ctx);
    }

    // natural property type: String?
    public Object text;
    // natural property type: Exception?
    public Object cause;
    // the associated native Java exception
    public nException $exception;

    /**
     * This is a static method that will be called by the naturally constructed sub-classes.
     * See {@link org.xvm.javajit.builders.CommonBuilder#assembleNew}.
     *
     * The name is known to be "construct" since it's the very first constructor at Exception.x
     *
     * @see {@link org.xvm.asm.constants.MethodConstant#ensureJitMethodName}
     */
    public static void construct(Ctx ctx, CtorCtx cctx, Exception thi$, Object message, Object cause) {
        thi$.text       = message instanceof String text ? text : Nullable.Null;
        thi$.cause      = cause instanceof Exception e ? e : Nullable.Null;
        thi$.$exception = thi$.$createJavaException(cause instanceof Exception e ? e.$exception : null);
    }

    /**
     * Helper method for native exception construction.
     */
    public nException $init(Ctx ctx, java.lang.String message) {
        return $init(ctx, message, null);
    }

    /**
     * Helper method for native exception construction.
     */
    public nException $init(Ctx ctx, java.lang.String message, Throwable cause) {
        this.text       = message == null ? Nullable.Null : String.of(ctx, message);
        this.cause      = cause instanceof nException e ? e.exception : Nullable.Null;
        this.$exception = $createJavaException(cause);
        return $exception;
    }

    /**
     * This method will be overridden by each subclass to instantiate a corresponding Java exception.
     */
    public nException $createJavaException(Throwable cause) {
        return new nException(cause, this);
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

    public static nException $ro(Ctx ctx, java.lang.String text) {
        return new ReadOnly(ctx).$init(ctx, text, null);
    }

    public static nException $oob(Ctx ctx, java.lang.String text) {
        return new OutOfBounds(ctx).$init(ctx, text, null);
    }

    public static nException $unsupported(Ctx ctx, java.lang.String text) {
        return new Unsupported(ctx).$init(ctx, text, null);
    }

    public static nException $typeMismatch(Ctx ctx, java.lang.String text) {
        return new TypeMismatch(ctx).$init(ctx, text, null);
    }

    /**
     * @return generic runtime exception
     */
    public static nException $rt(Ctx ctx, java.lang.String text) {
        return new Exception(ctx).$init(ctx, text, null);
    }

    // ----- Orderable interface -------------------------------------------------------------------

    /**
     * The native implementation of:
     *
     * static <CompileType extends Orderable> Ordered compare(CompileType value1, CompileType value2);
     */
    public static Ordered compare(Ctx ctx, nType type, Exception value1, Exception value2) {
        int i = Long.compare(hashCode$p(ctx, type, value1), hashCode$p(ctx, type, value2));
        return i < 0  ? Ordered.Lesser.$INSTANCE
             : i == 0 ? Ordered.Equal.$INSTANCE
                      : Ordered.Greater.$INSTANCE;
    }

    /**
     * The native implementation of:
     *
     *  static <CompileType extends Exception> Boolean equals(CompileType value1, CompileType value2)
     */
    public static boolean equals$p(Ctx ctx, nType type, Exception value1, Exception value2) {
        if (!nObj.equals$p(ctx, ((nObj) value1.text).$type(ctx), value1.text, value2.text)) {
            return false;
        }
        if (!nObj.equals$p(ctx, ((nObj) value1.cause).$type(ctx), value1.cause, value2.cause)) {
            return false;
        }
        return value1.$exception.equals(value2.$exception);
    }

    // ----- Hashable interface --------------------------------------------------------------------

    /**
     * The native implementation of:
     *
     * static <CompileType extends Hashable> Int hashCode(CompileType value);;
     */
    public static long hashCode$p(Ctx ctx, nType type, Exception ex) {
        return Objects.hash(ex.text, ex.cause, ex.$exception);
    }
}
