import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
import Ecstasy.fs.FileChannel;
import Ecstasy.fs.FileNotFound;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

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
