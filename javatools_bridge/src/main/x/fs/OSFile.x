import ecstasy.fs.Directory;
import ecstasy.fs.File;
import ecstasy.fs.FileChannel;
import ecstasy.fs.FileNotFound;
import ecstasy.fs.FileStore;
import ecstasy.fs.FileWatcher;
import ecstasy.fs.Path;

/**
 * Native OS File implementation.
 */
const OSFile
        extends OSFileNode
        implements File
    {
    @Override
    File truncate(Int newSize = 0);

    @Override
    conditional FileStore openArchive()
        {
        return False; // TODO
        }

    @Override
    Cancellable watch(FileWatcher watcher)
        {
        if (!parentDir.exists)
            {
            throw new FileNotFound(path, "No parent directory");
            }
        return store.watchFile(this, watcher);
        }

    @Override
    FileChannel open(ReadOption read=Read, WriteOption... write=[Write]);

    /**
     * The parent directory.
     */
    Directory parentDir.get()
        {
        assert Path parentPath ?= path.parent;
        assert File|Directory dir := store.find(parentPath);
        assert dir.is(Directory);
        return dir;
        }

    // ----- native --------------------------------------------------------------------------------

    @Override
    @Abstract immutable Byte[] contents;

    @Override
    @Abstract Int size;
    }
