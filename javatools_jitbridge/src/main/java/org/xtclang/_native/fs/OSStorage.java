package org.xtclang._native.fs;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.nService;

import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;

import org.xtclang.ecstasy.fs.Directory;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.ModuleLoader;

/**
 * The native implementation of {@code _native.fs.OSStorage}.
 */
public class OSStorage extends nService {

    public OSStorage() {
        ModuleLoader loader = (ModuleLoader) OSStorage.class.getClassLoader();
        Ctx          ctx    = loader.getCtx();
        super(ctx);
        fileStore = new OSFileStore(ctx, this, false);
    }

    /**
     * The singleton instance of {@link OSStorage}.
     */
    private static final OSStorage $INSTANCE = new OSStorage();

    /**
     * The {@link OSFileStore} property.
     */
    public OSFileStore fileStore;

    // ----- OSStorage.x implementation ------------------------------------------------------------

    /**
     * The native property getter for the property {@code OSFileStore fileStore;}
     */
    public OSFileStore fileStore$get(Ctx ctx) {
        return fileStore;
    }

    /**
     * The native implementation of the property {@code @Abstract @RO Directory homeDir;}.
     */
    public Directory homeDir$get(Ctx ctx) {
        boolean found = find$p(ctx, fileStore, String.of(ctx, System.getProperty("user.home")));
        if (!found) {
            throw Exception.$illegalState(ctx, "Assertion failed");
        }
        return (Directory) ctx.o0;
    }

    /**
     * The native implementation of the property {@code @Abstract @RO Directory curDir;}.
     */
    public Directory curDir$get(Ctx ctx) {
        boolean found = find$p(ctx, fileStore, String.of(ctx, System.getProperty("user.dir")));
        if (!found) {
            throw Exception.$illegalState(ctx, "Assertion failed");
        }
        return (Directory) ctx.o0;
    }

    /**
     * The native implementation of the property {@code @Abstract @RO Directory tmpDir;}.
     */
    public Directory tmpDir$get(Ctx ctx) {
        boolean found = find$p(ctx, fileStore, String.of(ctx, System.getProperty("java.io.tmpdir")));
        if (!found) {
            throw Exception.$illegalState(ctx, "Assertion failed");
        }
        return (Directory) ctx.o0;
    }

    /**
     * The native implementation of the method
     * {@code conditional Directory|File find(OSFileStore store, String pathString)}.
     */
    public boolean find$p(Ctx ctx, OSFileStore store, String pathString) {
        Path path = OSFileNode.$toOsPath(ctx, pathString);
        if (Files.exists(path)) {
            ctx.o0 = OSFileNode.$nodeFor(ctx, store, path);
            return true;
        }
        return false;
    }

    /**
     * The native implementation of the method {@code String[] names(String pathString)}.
     */
    public ArrayᐸObjectᐳ names(Ctx ctx, String pathString)      {
        Path               path = OSFileNode.$toOsPath(ctx, pathString);
        java.lang.String[] list = path.toFile().list();

        if (list == null) {
            list = new java.lang.String[0];
        }
        String[] names = Arrays.stream(list)
                               .map(name -> String.of(ctx, name))
                               .toArray(String[]::new);
        return ArrayᐸObjectᐳ.$makeStringArray(ctx, names);
    }

    /**
     * The native implementation of the method {@code Boolean createDir(String pathString)}.
     */
    public boolean  createDir$p(Ctx ctx, String pathString)  {
        java.nio.file.Path path = OSFileNode.$toOsPath(ctx, pathString);
        if (Files.exists(path) && Files.isDirectory(path)) {
            return false;
        }
        return path.toFile().mkdirs();
    }

    /**
     * The native implementation of the method {@code Boolean createFile(String pathString)}.
     */
    public boolean createFile$p(Ctx ctx, String pathString) {
        java.nio.file.Path path = OSFileNode.$toOsPath(ctx, pathString);
        if (Files.exists(path) && !Files.isDirectory(path)) {
            return false;
        }

        Path parent = path.getParent();
        if (!Files.exists(parent)) {
            throw Exception.$io(ctx, "Cannot create file, parent directory does not exist: " + path);
        }
        if (!Files.isDirectory(parent)) {
            throw Exception.$io(ctx, "Cannot create file, parent is not a directory: " + path);
        }
        try {
            return path.toFile().createNewFile();
        } catch (java.io.IOException e) {
            throw Exception.$io(ctx, e.getMessage(), e);
        }
    }

    /**
     * The native implementation of the method {@code Boolean delete(String pathString)}.
     */
    public boolean delete$p(Ctx ctx, String pathString) {
        Path path = OSFileNode.$toOsPath(ctx, pathString);
        if (Files.exists(path)) {
            return path.toFile().delete();
        }
        return false;
    }

    /**
     * The native implementation of the method {@code void watch(String pathStringDir)}.
     */
    void watch(Ctx ctx, String pathStringDir) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * The native implementation of the method {@code void unwatch(String pathStringDir)}.
     */
    void unwatch(Ctx ctx, String pathStringDir) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * The native implementation of the method {@code static OSStorage instance()}.
     */
    public static OSStorage instance(Ctx ctx) {
        return $INSTANCE;
    }
}
