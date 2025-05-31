import ecstasy.fs.AccessDenied;
import ecstasy.fs.FileChannel;
import ecstasy.fs.FileNotFound;
import ecstasy.fs.FileWatcher;

/**
 * Native OS File implementation.
 */
const OSFile
        extends OSFileNode
        implements File {

    @Override
    immutable Byte[] contents {
        immutable Byte[] get()                       = TODO("native");
        void             set(immutable Byte[] bytes) = TODO("native");
    }

    @Override
    immutable Byte[] read(Range<Int> range) {
        if (!exists) {
            throw new FileNotFound(path);
        }
        return readImpl(range);
    }

    @Override
    File truncate(Int newSize) {
        if (!exists) {
            throw new FileNotFound(path);
        }

        if (!writable) {
            throw new AccessDenied(path);
        }

        truncateImpl(newSize);

        return this;
    }

    @Override
    File append(Byte[] contents) {
        if (exists) {
            if (!writable) {
                throw new AccessDenied(path);
            }
            appendBytes(contents);
        } else {
            this.contents = contents.freeze(inPlace=False);
        }
        return this;
    }

    @Override
    File append(File file) {
        if (exists && !writable) {
            throw new AccessDenied(path);
        }
        if (OSFileNode osFile := unwrap(file)) {
            appendFile(osFile);
            return this;
        } else {
            // could be suboptimal, but it's unlikely we are getting here for large files
            super(file);
            return this;
        }
    }

    @Override
    conditional FileStore openArchive() {
        return False; // TODO
    }

    @Override
    Cancellable watch(FileWatcher watcher) {
        Directory? parent = this.parent;
        if (parent == Null || !parent.exists) {
            throw new AccessDenied(path, "Inaccessible parent directory");
        }
        return store.watchFile(this, watcher);
    }

    @Override
    FileChannel open(ReadOption read = Read, WriteOption[] write = [Write]) {
        RawOSFileChannel rawChannel  = openImpl(read, write);
        FileChannel      fileChannel = new OSFileChannel(rawChannel);
        return &fileChannel.maskAs(FileChannel);
    }


    // ----- native --------------------------------------------------------------------------------

    immutable Byte[] readImpl(Range<Int> range)                     = TODO("native");
    void truncateImpl(Int newSize)                                  = TODO("native");
    void appendBytes(Byte[] contents)                               = TODO("native");
    void appendFile(OSFileNode file)                                = TODO("native");
    RawOSFileChannel openImpl(ReadOption read, WriteOption[] write) = TODO("native");
}