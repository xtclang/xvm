import ecstasy.fs.AccessDenied;
import ecstasy.fs.Directory;
import ecstasy.fs.File;
import ecstasy.fs.FileNode;
import ecstasy.fs.FileStore;
import ecstasy.fs.FileWatcher;
import ecstasy.fs.Path;

/**
 * Native OS FileStore implementation.
 */
class OSFileStore
        implements FileStore {

    construct(OSStorage storage, Boolean readOnly) {
        this.storage  = storage;
        this.readOnly = readOnly;
    }

    @Override
    public/private Boolean readOnly;

    @Override
    @Lazy Directory root.calc() {
        assert Directory|File dir := find(Path.ROOT);
        return dir.as(Directory);
    }

    @Override
    conditional Directory|File find(Path path) = storage.find(this, path.toString());

    @Override
    Directory dirFor(Path path) = dirFor(path.toString());

    @Override
    File fileFor(Path path) = fileFor(path.toString());

    @Override
    Directory|File copy(Path source, Path dest) =
            copyOrMove(source, source.toString(), dest, dest.toString(), False);

    @Override
    Directory|File move(Path source, Path dest) =
            copyOrMove(source, source.toString(), dest, dest.toString(), True);

    @Override
    FileStore ensureReadOnly() {
        if (readOnly) {
            return this;
        }

        FileStore store = new OSFileStore(storage, True);
        return &store.maskAs(FileStore);
    }

    @Override
    @RO Int capacity;

    @Override
    @RO Int bytesUsed;

    @Override
    @RO Int bytesFree;

    @Override
    String toString() = $"FileStore: {root}";


    // ----- internal ------------------------------------------------------------------------------

    OSStorage storage;

    String[] names((protected OSDirectory) dir) = storage.names(dir.pathString);

    Boolean create((protected OSFileNode) node) {
        if (readOnly) {
            throw new AccessDenied(node.path, "Read-only store");
        }

        return node.is(OSDirectory)
            ? storage.createDir(node.pathString)
            : storage.createFile(node.pathString);
    }

    Boolean delete((protected OSFileNode) node) {
        if (readOnly) {
            throw new AccessDenied(node.path, "Read-only store");
        }

        return storage.delete(node.pathString);
    }

    conditional File linkAsFile((protected OSFileNode) node) = linkAsFile(node.pathString);

    Cancellable watchFile(OSFile file, FileWatcher watcher) = storage.watchFile(file.path, watcher);

    Cancellable watchDir(OSDirectory dir, FileWatcher watcher) = storage.watchDir(dir.path, watcher);


    // ----- native --------------------------------------------------------------------------------

    OSDirectory dirFor(String pathString) { TODO("native"); }

    OSFile fileFor(String pathString) { TODO("native"); }

    conditional OSFile linkAsFile(String pathString) { TODO("native"); }

    OSDirectory|OSFile copyOrMove(Path sourcePath, String sourceStr,
                                  Path destPath,   String destStr, Boolean move) { TODO("native"); }
}