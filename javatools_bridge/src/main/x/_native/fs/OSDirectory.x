import ecstasy.fs.FileNode;
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
    Iterator<Directory> dirs() = nodes().flatMap(node -> node.is(Directory) ? [node] : []);

    @Override
    Iterator<File> files() = nodes().flatMap(node -> node.is(File) ? [node] : []);

    private Iterator<FileNode> nodes() {
        return names().flatMap(name -> {
            // a name obtained from the listing may no longer resolve: it may be a broken link, or
            // may have been removed since the listing was taken - ignore it
            if (File|Directory node := find(name)) {
                return [node];
            }
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
