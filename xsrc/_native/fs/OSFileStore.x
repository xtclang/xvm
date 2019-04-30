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
    construct()
        {
        this.readOnly = False;
        }

    construct(Boolean readOnly)
        {
        this.readOnly = readOnly;
        }

    @Override
    @Lazy Directory root.calc()
        {
        assert Directory|File dir : find(Path.ROOT);
        return dir.as(Directory);
        }

    @Override
    conditional Directory|File find(Path path)
        {
        TODO
        }

    @Override
    Directory dirFor(Path path)
        {
        TODO
        }

    @Override
    File fileFor(Path path)
        {
        TODO
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
    public/private Boolean readOnly;

    @Override
    FileStore ensureReadOnly()
        {
        if (readOnly)
            {
            return this;
            }

        return new OSFileStore(True);
        }

    @Override
    @RO Int capacity;

    @Override
    @RO Int bytesUsed;

    @Override
    @RO Int bytesFree;
    }
