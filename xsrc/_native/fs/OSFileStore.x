import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
import Ecstasy.fs.FileNode;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

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
        assert Directory|File dir : find(Path.ROOT);
        return dir.as(Directory);
        }

    @Override
    conditional Directory|File find(Path path)
        {
        return storage.find(this, path.to<String>());
        }

    @Override
    Directory dirFor(Path path)
        {
        return storage.dirFor(this, path.to<String>());
        }

    @Override
    File fileFor(Path path)
        {
        return storage.fileFor(this, path.to<String>());
        }

    @Override
    Directory|File copy(Path source, Path dest)
        {
        TODO
        }

    @Override
    Directory|File move(Path source, Path dest)
        {
        TODO
        }

    @Override
    Cancellable watch(Path path, FileWatcher watch)
        {
        TODO
        }

    @Override
    Cancellable watchRecursively(Path path, FileWatcher watch)
        {
        TODO
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
    }
