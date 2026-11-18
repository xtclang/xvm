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

    public Object path;

    public nException $init(Ctx ctx, boolean async, java.lang.String message, Path path) {
        return $init(ctx, false, message, null, path);
    }

    public nException $init(Ctx ctx, boolean async, java.lang.String message, Throwable cause,
            Path path) {
        this.path = path == null ? Nullable.Null.$INSTANCE : path;
        return super.$init(ctx, false, message, cause);
    }

    // TODO remove this when PathException message.get() compiles correctly
    public String message$get(Ctx ctx, boolean async) {
        String message = (text instanceof String) ? (String) text : String.EmptyString;
        if (path instanceof Path p) {
            if (message.size$get$p(ctx, false) == 0) {
                return p.toString(ctx, false);
            }
            return String.of(ctx, false, p.toString(ctx,
                    false).toString() + "; " + message.toString(ctx, false));
        }
        return message;
    }
}
