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
    immutable Byte[] contents
        {
        immutable Byte[] get()                       {TODO("native");}
        void             set(immutable Byte[] bytes) {TODO("native");}
        }

    @Override
    Byte[] read(Range<Int> range)
        {
        if (!exists)
            {
            throw new FileNotFound(path);
            }
        return readImpl(range);
        }

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
    FileChannel open(ReadOption read=Read, WriteOption[] write = [Write]) {TODO("native");}


    // ----- native --------------------------------------------------------------------------------

    Byte[] readImpl(Range<Int> range) {TODO("native");}
    void truncateImpl(Int newSize)    {TODO("native");}
    void appendImpl(Byte[] contents)  {TODO("native");}
    }