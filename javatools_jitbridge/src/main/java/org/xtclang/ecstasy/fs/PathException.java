package org.xtclang.ecstasy.fs;

import org.xtclang.ecstasy.Nullable;
import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nException;

import org.xtclang.ecstasy.io.IOException;

import org.xtclang.ecstasy.text.String;
import org.xvm.javajit.Ctx;

/**
 * Native support of PathException.
 */
public class PathException extends IOException {
    public PathException(Ctx ctx) {
        super(ctx);
    }

    public Object path = Nullable.Null.$INSTANCE;

    public nException $init(Ctx ctx, java.lang.String message, Path path) {
        return $init(ctx, message, null, path);
    }

    public nException $init(Ctx ctx, java.lang.String message, Throwable cause, Path path) {
        this.path = path == null ? Nullable.Null.$INSTANCE : path;
        return super.$init(ctx, message, cause);
    }

    // TODO remove this when PathException message.get() compiles correctly
    public String message$get(Ctx ctx) {
        String message = (text instanceof String) ? (String) text : String.EmptyString;
        if (path instanceof Path p) {
            if (message.size$get$p(ctx) == 0) {
                return p.toString(ctx);
            }
            return String.of(ctx, p.toString(ctx).toString() + "; " + message.toString(ctx));
        }
        return message;
    }
}
