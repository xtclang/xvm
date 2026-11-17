package org.xtclang._native.fs;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.nObject;
import org.xtclang.ecstasy.Object;

import org.xtclang.ecstasy.fs.AccessDenied;
import org.xtclang.ecstasy.fs.FileAlreadyExists;
import org.xtclang.ecstasy.fs.FileNotFound;
import org.xtclang.ecstasy.fs.Path;
import org.xtclang.ecstasy.fs.Directory;
import org.xtclang.ecstasy.fs.FileStore;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;

/**
 * The native implementation of {@code _native.fs.OSFileStore}.
 */
@JitGenerated
public class OSFileStore
        extends nObject
        implements FileStore {

    private OSFileStore() {
        super(null);
    }

    /**
     * The primitive constructor
     * {@code construct(OSStorage storage, Boolean readOnly)}
     */
    @JitFromNatural
    public static OSFileStore $new$p(Ctx ctx, OSStorage storage, boolean readOnly) {
        // this method is declared so that other native code can call the constructor
        // the JIT will generate the code for this method from the natural code in
        // _native.fs.OSFileStore.x
        return null;
    }

    /**
     * The root of the Java file system.
     */
    private static final java.io.File $ROOT = new java.io.File("/");

    /**
     * The native implementation of {@code @Lazy Directory root.calc()}.
     */
    @JitFromNatural
    public Directory root$get(Ctx ctx) {
        // this method is declared so that other native code can access the root directory property
        // the JIT will generate the code for this method from the natural code in
        // _native.fs.OSFileStore.x
        return null;
    }

    /**
     * The native implementation of {@code @RO Int capacity;}.
     */
    public long capacity$get$p(Ctx ctx) {
        return $ROOT.getTotalSpace();
    }

    /**
     * The native implementation of {@code @RO Int bytesUsed;}.
     */
    public long bytesUsed$get$p(Ctx ctx) {
        return $ROOT.getTotalSpace() - $ROOT.getFreeSpace();
    }

    /**
     * The native implementation of {@code @RO Int bytesFree;}.
     */
    public long bytesFree$get$p(Ctx ctx) {
        return $ROOT.getFreeSpace();
    }

    /**
     * The native implementation of {@code Directory dirFor(Path path);}.
     */
    public Directory dirFor(Ctx ctx, Path path) {
        return dirFor(ctx, path.toString(ctx));
    }

    /**
     * The native implementation of {@code Directory dirFor(Path path);}.
     */
    public OSDirectory dirFor(Ctx ctx, String pathString) {
        java.nio.file.Path path = OSFileNode.$toOsPath(ctx, pathString);
        return new OSDirectory(ctx, this, path);
    }

    /**
     * The native implementation of {@code File fileFor(Path path);}.
     */
    public OSFile fileFor(Ctx ctx, String pathString) {
        java.nio.file.Path path = OSFileNode.$toOsPath(ctx, pathString);
        return new OSFile(ctx, this, path);
    }

    /**
     * The native implementation of the OSFileStore.x method:
     * {@code OSDirectory|OSFile copyOrMove(Path sourcePath, String sourceStr, Path destPath,
     *                                      String destStr, Boolean move)}.
     */
    public Object copyOrMove$p(Ctx ctx, Path sourcePath, String sourceStr, Path destPath,
                               String destStr, boolean move) {

        try {
            java.nio.file.Path pathSrc = OSFileNode.$toOsPath(ctx, sourceStr);
            if (Files.notExists(pathSrc)) {
                throw new FileNotFound(ctx)
                        .$init(ctx, "Could not find file or directory: " + sourceStr, sourcePath);
            }

            java.nio.file.Path pathDest = OSFileNode.$toOsPath(ctx, destStr);
            if (Files.exists(pathDest) && !Files.isDirectory(pathDest)) {
                throw new FileAlreadyExists(ctx)
                        .$init(ctx, "Could not overwrite file or directory: " + destStr, destPath);
            }

            java.nio.file.Path pathResult = move
                    ? Files.move(pathSrc, pathDest)
                    : Files.copy(pathSrc, pathDest);

            return OSFileNode.$nodeFor(ctx, this, pathResult);
        } catch (NoSuchFileException | FileNotFoundException e) {
            throw new FileNotFound(ctx).$init(ctx, e.getMessage(), sourcePath);
        } catch (FileAlreadyExistsException e) {
            throw new FileAlreadyExists(ctx).$init(ctx, e.getMessage(), destPath);
        } catch (SecurityException | AccessDeniedException e) {
            throw new AccessDenied(ctx).$init(ctx, e.getMessage(), destPath);
        } catch (IOException | InvalidPathException e) {
            throw Exception.$io(ctx, e.getMessage());
        }
    }

    /**
     * The native implementation of the OSFileStore.x method:
     * {@code conditional OSFile linkAsFile(String pathString)}.
     */
    public boolean linkFileAs$p(Ctx ctx, String pathString) {
        java.nio.file.Path pathSrc = OSFileNode.$toOsPath(ctx, pathString);
        // TODO implement native support for link files
        return false;
    }
}
