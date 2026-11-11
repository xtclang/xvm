package org.xtclang._native.fs;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;

import java.nio.file.attribute.BasicFileAttributes;

import org.xtclang.ecstasy.AppenderᐸCharᐳ;
import org.xtclang.ecstasy.nConst;

import org.xtclang.ecstasy.fs.FileNode;
import org.xtclang.ecstasy.fs.Path;

import org.xtclang.ecstasy.reflect.nRef;
import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

import org.xvm.runtime.template._native.fs.xOSFileNode;

import static org.xtclang.ecstasy.Exception.$io;

/**
 * Native implementation for {@code _native.fs.OSFileNode}.
 */
public class OSFileNode
        extends nConst
        implements FileNode {

    protected OSFileNode(Ctx ctx, OSFileStore store, java.nio.file.Path path) {
        super(ctx);
        this.store     = store;
        this.$javaPath = path;
        this.path      = new nRef(ctx, Path.$fromJavaPath(ctx, path), ctx.pool().typePath(), false);
    }

    /**
     * The native property {@code @RO Path path;}
     */
    public nRef path;

    /**
     * The field for the native property {@code OSFileStore store;}.
     */
    public OSFileStore store;

    /**
     * The Java {@link java.nio.file.Path} this {@link OSFileNode} represents.
     */
    protected java.nio.file.Path $javaPath;

    /**
     * The native property getter for {@code OSFileStore store;}
     */
    @Override
    public OSFileStore store$get(Ctx ctx) {
        return store;
    }

    /**
     * The native property getter for {@code @Lazy Path path.calc()}
     */
    public Path path$get(Ctx ctx) {
        nRef path = this.path;
        if (path == null) {
            path = this.path = new nRef(ctx, Path.$fromJavaPath(ctx, $javaPath), ctx.pool().typePath(), false);
        }
        return (Path) path.get(ctx);
    }

    /**
     * The primitive property getter for {@code Boolean exists.get()}.
     */
    public boolean exists$get$p(Ctx ctx) {
        return $getJavaPath(ctx).toFile().exists();
    }

    /**
     * The primitive property getter for {@code Int size.get()}.
     */
    @Override
    public long size$get$p(Ctx ctx) {
        return 0L;
    }

    /**
     * The primitive property getter for {@code Boolean readable.get()}.
     */
    public boolean readable$get$p(Ctx ctx) {
        return $getJavaPath(ctx).toFile().canRead();
    }

    /**
     * The primitive property getter for {@code Boolean writable.get()}.
     */
    public boolean writable$get$p(Ctx ctx) {
        return $getJavaPath(ctx).toFile().canWrite();
    }

    public long estimateStringLength$p(Ctx ctx) {
        return $javaPath.toString().length();
    }

    public AppenderᐸCharᐳ appendTo(Ctx ctx, AppenderᐸCharᐳ appender) {
        return pathString$get(ctx).appendTo(ctx, appender);
    }

//    @Override
//    public String toString(Ctx ctx) {
//        return path$get(ctx).toString(ctx);
//    }

    // ----- OSFileNode API ------------------------------------------------------------------------

    /**
     * The native property getter for {@code String pathString.get()}.
     */
    public String pathString$get(Ctx ctx) {
        return String.of(ctx, $javaPath.toString());
    }

    /**
     * The primitive property getter for {@code private Int createdMillis.get()}.
     */
    public long createdMillis$get$p(Ctx ctx) {
        try {
            java.nio.file.Path path = $getJavaPath(ctx);
            if (Files.exists(path)) {
                BasicFileAttributes attr = Files.readAttributes($getJavaPath(ctx), BasicFileAttributes.class);
                return attr.creationTime().toMillis();
            } else {
                return 0L;
            }
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive property getter for {@code private Int accessedMillis.get()}.
     */
    public long accessedMillis$get$p(Ctx ctx) {
        try {
            java.nio.file.Path path = $getJavaPath(ctx);
            if (Files.exists(path)) {
                BasicFileAttributes attr = Files.readAttributes($getJavaPath(ctx), BasicFileAttributes.class);
                return attr.lastAccessTime().toMillis();
            } else {
                return 0L;
            }
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    /**
     * The primitive property getter for {@code private Int modifiedMillis.get()}.
     */
    public long modifiedMillis$get$p(Ctx ctx) {
        try {
            java.nio.file.Path path = $getJavaPath(ctx);
            if (Files.exists(path)) {
                BasicFileAttributes attr = Files.readAttributes($getJavaPath(ctx), BasicFileAttributes.class);
                return attr.lastModifiedTime().toMillis();
            } else {
                return 0L;
            }
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }

    // ----- JIT helper methods --------------------------------------------------------------------

    /**
     * @return the path from the {@link #path} ref for this node
     */
    protected Path $getPath(Ctx ctx) {
        return (Path) path.get(ctx);
    }

    /**
     * @return the Java {@code java.nio.file.Path} for this node
     */
    protected java.nio.file.Path $getJavaPath(Ctx ctx) {
        java.nio.file.Path path = $javaPath;
        if (path == null) {
            path = $javaPath = $toOsPath(ctx, pathString$get(ctx));
        }
        return path;
    }

    /**
     * A utility method to convert a string path to an absolute Java path.
     */
    public static java.nio.file.Path $toOsPath(Ctx ctx, String pathString) {
        try {
            return xOSFileNode.toOsPath(pathString.toString()).toAbsolutePath();
        } catch (InvalidPathException e) {
            throw $io(ctx, e.getMessage());
        }
    }

    /**
     * A utility method to create either an {@link OSDirectory} or an {@link OSFile} based on the
     * provided path.
     */
    public static OSFileNode $nodeFor(Ctx ctx, OSFileStore store, java.nio.file.Path path) {
        return Files.isDirectory(path)
                ? new OSDirectory(ctx, store, path)
                : new OSFile(ctx, store, path);
    }
}
