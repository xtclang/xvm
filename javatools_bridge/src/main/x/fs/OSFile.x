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
        Directory parent = this.parent ?: assert;
        if (!parent.exists)
            {
            throw new FileNotFound(path, "No parent directory");
            }
        return store.watchFile(this, watcher);
        }

    @Override
    FileChannel open(ReadOption read=Read, WriteOption[] write = [Write]);

    @Override
    void append(Byte[] contents);

    // ----- native --------------------------------------------------------------------------------

    @Override immutable Byte[] contents.get() { TODO("native"); }
    @Override Int size.get()                  { TODO("native"); }
    }
