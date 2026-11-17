package org.xtclang._native.fs;

import java.io.IOException;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;

import java.nio.file.attribute.BasicFileAttributes;

import org.xtclang.ecstasy.fs.Directory;

import org.xvm.javajit.Ctx;

import static org.xtclang.ecstasy.Exception.$io;

/**
 * Native implementation for {@code _native.fs.OSDirectory}.
 */
public class OSDirectory
        extends OSFileNode
        implements Directory {

    public OSDirectory(Ctx ctx, OSFileStore store, java.nio.file.Path path) {
        super(ctx, store, path);
    }

    /**
     * The primitive implementation of the OSDirectory.x method {@code Boolean deleteRecursively();}.
     */
    public boolean deleteRecursively$p(Ctx ctx) {
        Path path = $getJavaPath(ctx);
        if (path.toFile().isDirectory()) {
            try {
                Files.walkFileTree(path, nDeleteVisitor.$INSTANCE);
                return true;
            } catch (IOException e) {
                throw $io(ctx, e);
            }
        }
        return false;
    }

    // ----- inner class nDeleteVisitor ------------------------------------------------------------

    /**
     * A file visitor to recursively delete files and directories.
     */
    protected static class nDeleteVisitor extends SimpleFileVisitor<Path> {
        /**
         * A singleton instance of {@link nDeleteVisitor}.
         */
        public static final nDeleteVisitor $INSTANCE = new nDeleteVisitor();

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }
}
