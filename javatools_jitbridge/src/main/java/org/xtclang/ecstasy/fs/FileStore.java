package org.xtclang.ecstasy.fs;

import org.xtclang.ecstasy.Object;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * The native implementation of {@code ecstasy.fs.Directory.x}.
 */
@JitGenerated
public interface FileStore {
    /**
     * The native implementation of {@code Directory dirFor(Path path);}.
     */
    @JitName
    Directory dirFor(Ctx ctx, Path path);

    /**
     * The native implementation of {@code File fileFor(Path path);}.
     */
    @JitName
    File fileFor(Ctx ctx, Path path);

    /**
     * The native implementation of {@code conditional Directory|File find(Path path);}.
     */
    @JitName
    boolean find$p(Ctx ctx, Path path);

    /**
     * The native implementation of {@code Directory|File copy(Path source, Path dest);}.
     */
    @JitName
    Object copy(Ctx ctx, Path source, Path dest);

    /**
     * The native implementation of {@code Directory|File move(Path source, Path dest);}.
     */
    @JitName
    Object move(Ctx ctx, Path source, Path dest);

    /**
     * The native implementation of {@code FileStore ensureReadOnly();}.
     */
    @JitName
    FileStore ensureReadOnly(Ctx ctx);
}
