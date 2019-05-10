import Ecstasy.fs.FileNode;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Native OS File implementation.
 */
class OSFileNode
        implements FileNode
    {
    @Override
    @Lazy Path path.calc()
        {
        return new Path(pathString);
        }

    @Override
    @RO String name.get()
        {
        return path.form == Root ? "" : path.name;
        }

    @Override
    @RO Boolean exists;

    @Override
    @Lazy DateTime created.calc()
        {
        return new DateTime(createdMillis*Time.PICOS_PER_MILLI, TimeZone.UTC);
        }

    @Override
    DateTime modified.get()
        {
        return new DateTime(modifiedMillis*Time.PICOS_PER_MILLI, TimeZone.UTC);
        }

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

    @Override
    String to<String>()
        {
        return pathString;
        }

    // ----- native --------------------------------------------------------------------------------

    @Abstract protected OSStorage   storage;
    @Abstract protected String      pathString;

    @Abstract private Int           createdMillis;
    @Abstract private Int           modifiedMillis;
    }
