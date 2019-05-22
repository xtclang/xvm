import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.File;
import Ecstasy.fs.FileNode;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Constant Pool FileNode implementation.
 */
const CPFileNode(CPFileStore:protected store, Object cookie, Path path, DateTime created, DateTime modified, Int size)
        implements FileNode
        implements Stringable
    {
    @Override
    @RO String name.get()
        {
        return path.form == Root ? "" : path.name;
        }

    @Override
    @RO Boolean exists.get()
        {
        DEBUG;
        return cookie != Null;
        }

    @Override
    conditional File linkAsFile()
        {
        return False; // not implemented yet
        }

    @Override
    @RO DateTime accessed.get()
        {
        return DateTime.EPOCH;
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


    // ----- native support ------------------------------------------------------------------------

    /**
     * The ConstantPool FileStore that created this node.
     */
    protected CPFileStore:protected store;

    /**
     * The native handle for the constant which this node represents.
     */
    protected Object cookie;
    }
