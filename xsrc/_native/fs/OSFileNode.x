import Ecstasy.fs.FileNode;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Native OS File implementation.
 */
class OSFileNode
        implements FileNode
    {
    construct OSFileNode(Path path)
        {
        assert path.absolute;
        this.path = path.normalize();
        }

    @Override
    @RO Path path;

    @Override
    @RO String name.get()
        {
        return path.form == Root ? "" : path.
        }

    @Override
    @RO Boolean exists;

    @Override
    @RO DateTime created;

    @Override
    DateTime modified;

    @Override
    @RO DateTime accessed;

    @Override
    @RO Boolean readable;

    @Override
    @RO Boolean writable;

    @Override
    conditional FileNode create();

    @Override
    FileNode ensure();

    @Override
    conditional FileNode delete();

    @Override
    conditional FileNode renameTo(String name);

    @Override
    @RO Int size;

    @Override
    Cancellable watch(FileWatcher watch)
        {
        TODO
        }
    }
