package org.xtclang._native.fs;

import java.io.IOException;

import java.nio.file.Path;

import org.xtclang.ecstasy.fs.Directory;

import org.xvm.javajit.Ctx;

import org.xvm.util.Files;

import static org.xtclang.ecstasy.Exception.$io;

/**
 * Native implementation for {@code _native.fs.OSDirectory}.
 */
public class OSDirectory
        extends OSFileNode
        implements Directory {

    public OSDirectory(Ctx ctx, OSFileStore store, Path path) {
        super(ctx, store, path);
    }

    /**
     * The primitive implementation of the OSDirectory.x method {@code Boolean deleteRecursively();}.
     */
    public boolean deleteRecursively$p(Ctx ctx) {
        try {
            return Files.deleteRecursively($getJavaPath(ctx));
        } catch (IOException e) {
            throw $io(ctx, e);
        }
    }
}
