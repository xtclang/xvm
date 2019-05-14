import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Native OS Directory implementation.
 */
class OSDirectory
        extends OSFileNode
        implements Directory
    {
    @Override
    Iterator<String> names()
        {
        return storage.names(this).iterator();
        }

    @Override
    Iterator<Directory> dirs();

    @Override
    Iterator<File> files();

    @Override
    Iterator<File> filesRecursively();

    @Override
    conditional Directory|File find(String name);

    @Override
    Directory dirFor(String name);

    @Override
    File fileFor(String name);

    @Override
    conditional Directory deleteRecursively();

    @Override
    Cancellable watchRecursively(FileWatcher watch);
    }
