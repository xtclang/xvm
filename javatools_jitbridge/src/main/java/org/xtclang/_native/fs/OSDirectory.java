package org.xtclang._native.fs;

import java.io.File;
import java.io.IOException;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;

import java.nio.file.attribute.BasicFileAttributes;

import java.util.Collections;
import java.util.List;

import java.util.stream.Stream;

import org.xtclang.ecstasy.Iterator;
import org.xtclang.ecstasy.nObject;
import org.xtclang.ecstasy.nType;

import org.xtclang.ecstasy.fs.Directory;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

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
     * The primitive property getter for {@code Int size.get()}.
     */
    public long size$get$p(Ctx ctx) {
        java.nio.file.Path path = $getJavaPath(ctx);
        if (Files.exists(path)) {
            try (Stream<java.nio.file.Path> stream = Files.walk(path, 1)) {
                return stream.map(java.nio.file.Path::toFile)
                             .filter(File::isFile)
                             .mapToLong(File::length)
                             .sum();
            } catch (IOException e) {
                throw $io(ctx, e);
            }
        } else {
            return 0L;
        }
    }

    /**
     * The native implementation of the Directory.x method {@code Iterator<Directory> dirs();}.
     */
    public Iterator dirs(Ctx ctx) {
        return new nIterator(ctx, $getJavaPath(ctx), ctx.pool().typeDirectory(), 1);
    }

    /**
     * The native implementation of the Directory.x method {@code Iterator<File> files();}.
     */
    public Iterator files(Ctx ctx) {
        return new nIterator(ctx, $getJavaPath(ctx),ctx.pool().typeFile(), 2);
    }

    /**
     * The primitive implementation of the Directory.x method {@code Boolean deleteRecursively();}.
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

    /**
     * The native implementation of the Directory.x method {@code Iterator<File> filesRecursively();}.
     */
    public Iterator filesRecursively(Ctx ctx) {
        return new nRecursiveIterator(ctx, $getJavaPath(ctx));
    }

    /**
     * The native implementation of the OSDirectory.x method {@code Iterator<File> nodes();}.
     */
    public Iterator nodes(Ctx ctx) {
        return new nIterator(ctx, $getJavaPath(ctx), ctx.pool().typeFileNode(), 0);
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

    // ----- inner class nIterator -----------------------------------------------------------------

    /**
     * A native implementation of Iterator.x to iterate over this directory's files and directories.
     */
    private class nIterator extends nObject implements Iterator {

        private final java.util.Iterator<File> fileIterator;

        private final TypeConstant type;

        public nIterator(Ctx ctx, java.nio.file.Path path, TypeConstant type, int filesOrDirs) {
            super(ctx);
            this.type = type;
            if (Files.exists(path)) {
                File[] files = switch (filesOrDirs) {
                    case 1 -> path.toFile().listFiles(File::isDirectory);
                    case 2 -> path.toFile().listFiles(File::isFile);
                    default -> path.toFile().listFiles();
                };
                fileIterator = files != null
                                    ? List.of(files).iterator()
                                    : Collections.emptyIterator();
            } else {
                fileIterator = Collections.emptyIterator();
            }
        }

        @Override
        public TypeConstant $xvmType(Ctx ctx) {
            ConstantPool pool = ctx.pool();
            return pool.ensureParameterizedTypeConstant(
                    pool.typeIterator(), type);
        }

        @Override
        public boolean $isImmut() {
            return false;
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, type);
        }

        @Override
        public boolean next$p(Ctx ctx) {
            if (fileIterator.hasNext()) {
                ctx.o0 = OSFileNode.$nodeFor(ctx, store, fileIterator.next().toPath());
                return true;
            }
            return false;
        }
    }

    // ----- inner class nRecursiveIterator --------------------------------------------------------

    /**
     * A native implementation of Iterator.x to recursively iterate over this directory's files and
     * directories.
     */
    private class nRecursiveIterator extends nObject implements Iterator {

        private final nIterator dirIterator;

        private final nIterator fileIterator;

        private final TypeConstant type;

        private nRecursiveIterator iterator = null;

        public nRecursiveIterator(Ctx ctx, java.nio.file.Path path) {
            super(ctx);
            this.type    = ctx.pool().typeFile();
            dirIterator  = new nIterator(ctx, path, ctx.pool().typeDirectory(), 1);
            fileIterator = new nIterator(ctx, path, ctx.pool().typeFile(), 2);
        }

        @Override
        public TypeConstant $xvmType(Ctx ctx) {
            ConstantPool pool = ctx.pool();
            return pool.ensureParameterizedTypeConstant(
                    pool.typeIterator(), type);
        }

        @Override
        public boolean $isImmut() {
            return false;
        }

        @Override
        public nType Element$get(Ctx ctx) {
            return nType.$ensureType(ctx, type);
        }

        @Override
        public boolean next$p(Ctx ctx) {
            if (fileIterator.next$p(ctx)) {
                return true;
            }
            while (iterator == null || !iterator.next$p(ctx)) {
                if (dirIterator.next$p(ctx)) {
                    OSFileNode node = (OSFileNode) ctx.o0;
                    iterator = new nRecursiveIterator(ctx, node.$getJavaPath(ctx));
                } else {
                    return false;
                }
            }
            return iterator != null;
        }
    }
}
