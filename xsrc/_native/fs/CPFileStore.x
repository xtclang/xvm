import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Constant Pool FileStore implementation.
 */
const CPFileStore(Directory root)
        implements FileStore
    {
//    @Override
//    Directory root;

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
        throw new AccessDenied();
        }

    @Override
    Directory|File move(Path source, Path dest)
        {
        throw new AccessDenied();
        }

    @Override
    Cancellable watch(Path path, FileWatcher watch)
        {
        return () -> {};
        }

    @Override
    Cancellable watchRecursively(Path path, FileWatcher watch)
        {
        return () -> {};
        }

    @Override
    @RO Boolean readOnly.get()
        {
        return True;
        }

    @Override
    FileStore ensureReadOnly()
        {
        return this;
        }

    @Override
    @RO Int capacity.get()
        {
        return root.size;
        }

    @Override
    @RO Int bytesUsed.get()
        {
        return root.size;
        }


    @Override
    @RO Int bytesFree.get()
        {
        return 0;
        }
    }
