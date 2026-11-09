package org.xtclang.ecstasy.fs;

import org.xtclang.ecstasy.Object;

import org.xtclang.ecstasy.collections.Hashable;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * The native implementation of {@code ecstasy.fs.Directory.x}.
 */
public interface FileNode extends Object, Hashable {
    /**
     * The native property getter for {@code @RO FileStore store;}.
     */
    FileStore store$get(Ctx ctx);

    /**
     * The primitive property getter for {@code Int size.get()}.
     */
    long size$get$p(Ctx ctx);
}
