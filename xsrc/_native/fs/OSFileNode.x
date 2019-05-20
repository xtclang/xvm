import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
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
    conditional File linkAsFile()
        {
        return False; // TODO
        }

    @Override
    @Lazy DateTime created.calc()
        {
        // TODO: should be the "local" timezone
        return new DateTime(createdMillis*Time.PICOS_PER_MILLI, TimeZone.UTC);
        }

    @Override
    DateTime modified.get()
        {
        return new DateTime(modifiedMillis*Time.PICOS_PER_MILLI, TimeZone.UTC);
        }

    @Override
    @RO DateTime accessed.get()
        {
        return new DateTime(accessedMillis*Time.PICOS_PER_MILLI, TimeZone.UTC);
        }

    @Override
    @RO Boolean readable;

    @Override
    @RO Boolean writable;

    @Override
    Boolean create()
        {
        if (!exists)
            {
            if (store.readOnly)
                {
                throw new AccessDenied(path, "Read-only store");
                }

            return this.is(Directory)
                ? store.storage.createDir(store, pathString)
                : store.storage.createFile(store, pathString);
            }
        return False;
        }

    @Override
    FileNode ensure()
        {
        if (!exists)
            {
            create();
            }
        return this;
        }

    @Override
    Boolean delete()
        {
        if (exists)
            {
            if (store.readOnly)
                {
                throw new AccessDenied(path, "Read-only store");
                }

            return store.storage.delete(store, pathString);
            }
        return False;
        }

    @Override
    conditional FileNode renameTo(String name);

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

    // ----- internal -----------------------------------------------------------------------------


    // ----- native --------------------------------------------------------------------------------

    @Abstract protected OSFileStore store;
    @Abstract protected String      pathString;

    @Override
    @Abstract Int size;

    @Abstract private Int           createdMillis;
    @Abstract private Int           accessedMillis;
    @Abstract private Int           modifiedMillis;
    }
