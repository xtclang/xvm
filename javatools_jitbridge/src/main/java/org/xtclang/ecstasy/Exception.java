package org.xtclang.ecstasy;

import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;

import org.xtclang.ecstasy.fs.AccessDenied;

import org.xtclang.ecstasy.fs.FileNotFound;
import org.xtclang.ecstasy.fs.Path;
import org.xtclang.ecstasy.io.IOException;

import org.xvm.javajit.Ctx;

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
     * Exception Constructor:
     *  construct(String? text = Null, Exception? cause = Null)
     */
    public static Exception $new(Ctx ctx, Object text, Object cause) {
        Exception ex = new Exception(ctx);
        construct(ctx, ex, text, cause);
        return ex;
    }

    /**
     * This is a static method that will be called by the naturally constructed subclasses.
     * See {@link org.xvm.javajit.builders.CommonBuilder#assembleNew}.
     *
     * <p>The name is known to be "construct" since it's the very first constructor at Exception.x
     *
     * @see org.xvm.asm.constants.MethodConstant#ensureJitMethodName
     */
    public static void construct(Ctx ctx, Exception thi$, Object message, Object cause) {
        thi$.text       = message instanceof String text ? text : Nullable.Null.$INSTANCE;
        thi$.cause      = cause instanceof Exception e ? e : Nullable.Null.$INSTANCE;
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
        this.text       = message == null ? Nullable.Null.$INSTANCE : String.of(ctx, message);
        this.cause      = cause instanceof nException e ? e.exception : Nullable.Null.$INSTANCE;
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
          .append(text == Nullable.Null.$INSTANCE ? "" : text.toString($ctx()));
        if ($exception != null) { // can be null only during construction
            for (StackTraceElement el : $exception.getStackTrace()) {
                if (el.getFileName().endsWith(".x") && !el.getMethodName().startsWith("$")) {
                    sb.append("\n    at ")
                      .append(el);
                }
            }
        }
        return sb.toString();
    }

    public static nException $ro(Ctx ctx, java.lang.String text) {
        return new ReadOnly(ctx).$init(ctx, text);
    }

    public static nException $oob(Ctx ctx, long index, long start, long end) {
        return $oob(ctx, "Index " + index + " out of range " + start + ".." + end);
    }

    public static nException $oob(Ctx ctx, java.lang.String text) {
        return new OutOfBounds(ctx).$init(ctx, text);
    }

    public static nException $unsupported(Ctx ctx, java.lang.String text) {
        return new Unsupported(ctx).$init(ctx, text);
    }

    public static nException $typeMismatch(Ctx ctx, java.lang.String text) {
        return new TypeMismatch(ctx).$init(ctx, text);
    }

    /**
     * @return generic runtime exception
     */
    public static nException $rt(Ctx ctx, java.lang.String text) {
        return new Exception(ctx).$init(ctx, text, null);
    }

    public static nException $illegalArg(Ctx ctx, java.lang.String text) {
        return new IllegalArgument(ctx).$init(ctx, text);
    }

    public static nException $illegalState(Ctx ctx, java.lang.String text) {
        return new IllegalState(ctx).$init(ctx, text);
    }

    public static nException $io(Ctx ctx, java.lang.Exception ex) {
        if (ex instanceof java.nio.file.AccessDeniedException
            || ex instanceof NonReadableChannelException
            || ex instanceof NonWritableChannelException) {
            return new AccessDenied(ctx).$init(ctx, ex.getMessage());
        }
        if (ex instanceof java.io.IOException) {
            if (ex instanceof java.io.FileNotFoundException) {
                return new FileNotFound(ctx).$init(ctx, ex.getMessage());
            }
            return $io(ctx, ex.getMessage(), null);
        }
        return $io(ctx, ex.getMessage(), ex);
    }

    public static nException $io(Ctx ctx, java.lang.String text) {
        return $io(ctx, text, null);
    }

    public static nException $io(Ctx ctx, java.lang.String text, Throwable cause) {
        return new IOException(ctx).$init(ctx, text, cause);
    }

    public static nException $fileNotFound(Ctx ctx, Path path) {
        return $fileNotFound(ctx, "", path);
    }

    public static nException $fileNotFound(Ctx ctx, java.lang.String text, Path path) {
        return new FileNotFound(ctx).$init(ctx, text, path);
    }

    public static nException $accessDenied(Ctx ctx, Path path) {
        return $accessDenied(ctx, "", path);
    }

    public static nException $accessDenied(Ctx ctx, java.lang.String text, Path path) {
        return new AccessDenied(ctx).$init(ctx, text, path);
    }

    public static nException $xvmError(Ctx ctx, java.lang.String text, Throwable cause) {
        return new XvmError(ctx).$init(ctx, text, cause);
    }

    // ----- Const interface -----------------------------------------------------------------------

    /**
     * Native implementation of:
     *
     * <pre>{@code
     *     static <CompileType extends Exception> Boolean equals(CompileType value1,
     *                                                           CompileType value2)
     * }</pre>
     */
    public static boolean equals$p(Ctx ctx, nType type, Exception value1, Exception value2) {
        return value1 == value2;
    }

    /**
     * Native implementation of:
     *
     * <pre>{@code
     *     static <CompileType extends Exception> Ordered compare(CompileType value1,
     *                                                            CompileType value2)
     * }</pre>
     */
    public static Ordered compare(Ctx ctx, nType type, Exception value1, Exception value2) {
        int result = Integer.compare(
                System.identityHashCode(value1), System.identityHashCode(value2));
        return result < 0  ? Ordered.Lesser.$INSTANCE
             : result == 0 ? Ordered.Equal.$INSTANCE
                           : Ordered.Greater.$INSTANCE;
    }

    /**
     * Native implementation of:
     *
     * <pre>{@code
     *     static <CompileType extends Exception> Int64 hashCode(CompileType value)
     * }</pre>
     */
    public static long hashCode$p(Ctx ctx, nType type, Exception value) {
        return System.identityHashCode(value);
    }
}
