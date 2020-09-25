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
        implements FileStore
    {
    construct(OSStorage storage, Boolean readOnly)
        {
        this.storage  = storage;
        this.readOnly = readOnly;
        }

    @Override
    public/private Boolean readOnly;

    @Override
    @Lazy Directory root.calc()
        {
        assert Directory|File dir := find(Path.ROOT);
        return dir.as(Directory);
        }

    @Override
    conditional Directory|File find(Path path)
        {
        return storage.find(this, path.toString());
        }

    @Override
    Directory dirFor(Path path)
        {
        return dirFor(path.toString());
        }

    @Override
    File fileFor(Path path)
        {
        return fileFor(path.toString());
        }

    @Override
    Directory|File copy(Path source, Path dest)
        {
        return copyOrMove(source, source.toString(), dest, dest.toString(), false);
        }

    @Override
    Directory|File move(Path source, Path dest)
        {
        return copyOrMove(source, source.toString(), dest, dest.toString(), true);
        }

    @Override
    FileStore ensureReadOnly()
        {
        if (readOnly)
            {
            return this;
            }

        return new OSFileStore(storage, True);
        }

    @Override
    @RO Int capacity;

    @Override
    @RO Int bytesUsed;

    @Override
    @RO Int bytesFree;


    // ----- internal ------------------------------------------------------------------------------

    OSStorage storage;

    String[] names(OSDirectory:protected dir)
        {
        return storage.names(dir.pathString);
        }

    Boolean create(OSFileNode:protected node)
        {
        if (readOnly)
            {
            throw new AccessDenied(node.path, "Read-only store");
            }

        return node.is(OSDirectory)
            ? storage.createDir(node.pathString)
            : storage.createFile(node.pathString);
        }

    Boolean delete(OSFileNode:protected node)
        {
        if (readOnly)
            {
            throw new AccessDenied(node.path, "Read-only store");
            }

        return storage.delete(node.pathString);
        }

    Cancellable watchFile(OSFile file, FileWatcher watcher)
        {
        return storage.watchFile(file.path, watcher);
        }

    Cancellable watchDir(OSDirectory dir, FileWatcher watcher)
        {
        return storage.watchDir(dir.path, watcher);
        }

    // ----- native --------------------------------------------------------------------------------

    OSDirectory dirFor(String pathString)
        {
        TODO native
        }

    OSFile fileFor(String pathString)
        {
        TODO native
        }

    OSDirectory|OSFile copyOrMove(Path sourcePath, String sourceStr, Path destPath, String destStr, Boolean move)
        {
        TODO native
        }
    }
