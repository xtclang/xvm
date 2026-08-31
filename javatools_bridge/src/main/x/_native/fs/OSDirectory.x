import ecstasy.fs.FileWatcher;

/**
 * Native OS Directory implementation.
 */
const OSDirectory
        extends OSFileNode
        implements Directory {

    @Override
    Int size.get() {
        Int size = 0;
        for (File file : files()) {
            size += file.size;
        }
        return size;
    }

    @Override
    Iterator<String> names() = store.names(this:protected).iterator();

    @Override
    Iterator<Directory> dirs() {
        return names().flatMap(name -> {
            if (File|Directory node := find(name), node.is(Directory)) {
                return [node];
            }
            // the name came from a listing, but the node may be a broken link, or may have been
            // removed since that listing was taken; either way it is simply not a directory now
            return [];
        });
    }

    @Override
    Iterator<File> files() {
        return names().flatMap(name -> {
            if (File|Directory node := find(name), node.is(File)) {
                return [node];
            }
            // the name came from a listing, but the node may be a broken link, or may have been
            // removed since that listing was taken; either way it is simply not a file now
            return [];
        });
    }

    @Override
    conditional Directory|File find(String name) {
        return name.size == 0
                ? (True, this)
                : store.find(path + name);
    }

    @Override
    Directory dirFor(String name) = store.dirFor(path + name);

    @Override
    File fileFor(String name) = store.fileFor(path + name);

    @Override
    Boolean deleteRecursively() { TODO("native"); }

    @Override
    Cancellable watch(FileWatcher watcher) = store.watchDir(this, watcher);

    @Override
    Cancellable watchRecursively(FileWatcher watcher) { TODO("native"); }
}
