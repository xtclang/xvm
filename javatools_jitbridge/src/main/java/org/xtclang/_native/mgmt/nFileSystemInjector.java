package org.xtclang._native.mgmt;

import org.xtclang._native.fs.OSStorage;
import org.xtclang.ecstasy.fs.Directory;
import org.xtclang.ecstasy.fs.FileStore;

import org.xvm.javajit.Xvm;

/**
 * A helper class to handle file system injectables.
 */
public class nFileSystemInjector {

    public nFileSystemInjector(Xvm xvm) {
        this.xvm = xvm;
    }

    private final Xvm xvm;

    private OSStorage osStorage;

    public FileStore ensureFileStore(Object ignored) {
        return ensureStorage().fileStore;
    }

    public Directory ensureRootDir(Object ignored) {
        return ensureStorage().fileStore.root$get(xvm.getCtx());
    }

    public Directory ensureHomeDir(Object opts) {
        return ensureStorage().homeDir$get(xvm.getCtx());
    }

    public Directory ensureCurDir(Object opts) {
        return ensureStorage().curDir$get(xvm.getCtx());
    }

    public Directory ensureTmpDir(Object opts) {
        return ensureStorage().tmpDir$get(xvm.getCtx());
    }

    protected OSStorage ensureStorage() {
        OSStorage osStorage = this.osStorage;
        if (osStorage == null) {
            synchronized (this) {
                osStorage = this.osStorage;
                if (osStorage == null) {
                    osStorage = this.osStorage = OSStorage.instance(xvm.getCtx());
                }
            }
        }
        return osStorage;
    }
}
