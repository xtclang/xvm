import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.File;
import Ecstasy.fs.FileNode;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Constant Pool FileNode implementation.
 */
const CPFileNode(CPFileStore store, Path path, DateTime created, DateTime modified, Boolean exists)
        implements FileNode
        implements Stringable
    {
//    @Override
//    Path path;

    @Override
    @RO String name.get()
        {
        return path.form == Root ? "" : path.name;
        }

//    @Override
//    Boolean exists;

    @Override
    conditional File linkAsFile()
        {
        return False;
        }

//    @Override
//    DateTime created;
//
//    @Override
//    DateTime modified;

    @Override
    @RO DateTime accessed.get()
        {
        return EPOCH;
        }

    @Override
    @RO Boolean readable.get()
        {
        return True;
        }

    @Override
    @RO Boolean writable.get()
        {
        return False;
        }

    @Override
    Boolean create()
        {
        if (exists)
            {
            return False;
            }
        throw new AccessDenied();
        }

    @Override
    FileNode ensure()
        {
        if (exists)
            {
            return this;
            }
        throw new AccessDenied();
        }

    @Override
    Boolean delete()
        {
        if (!exists)
            {
            return False;
            }
        throw new AccessDenied();
        }

    @Override
    conditional FileNode renameTo(String name)
        {
        throw new AccessDenied();
        }

    @Override
    Cancellable watch(FileWatcher watch)
        {
        return () -> {};
        }

    @Override
    @Abstract @RO Int size;

    @Override
    String to<String>()
        {
        return path.to<String>();
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return path.estimateStringLength();
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        path.appendTo(appender);
        }


    // ----- constants -----------------------------------------------------------------------------

    static DateTime EPOCH = new DateTime(0, TimeZone.UTC);
    }
