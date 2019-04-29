/**
 * Native OS FileStore implementation.
 */
class OSFileStore(Directory root, Boolean readOnly)
    {
    @Override
    public/private Directory root;

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

        return new OSFileStore(root, True);
        }

    @Override
    @RO Int capacity;

    @Override
    @RO Int bytesUsed;

    @Override
    @RO Int bytesFree;
    }
