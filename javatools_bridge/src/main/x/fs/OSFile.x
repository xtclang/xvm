import ecstasy.fs.AccessDenied;
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
    File truncate(Int newSize = 0)
        {
        if (!exists)
            {
            throw new FileNotFound(path);
            }

        if (!writable)
            {
            throw new AccessDenied(path);
            }

        truncateImpl(newSize);

        return this;
        }

    @Override
    File append(Byte[] contents)
        {
        if (!exists)
            {
            throw new FileNotFound(path);
            }

        if (!writable)
            {
            throw new AccessDenied(path);
            }

        appendImpl(contents);

        return this;
        }

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


    // ----- native --------------------------------------------------------------------------------

    void truncateImpl(Int newSize);
    void appendImpl(Byte[] contents);

    @Override immutable Byte[] contents.get() { TODO("native"); }
    @Override Int size.get()                  { TODO("native"); }
    }
