import ecstasy.fs.Directory;
import ecstasy.fs.File;
import ecstasy.fs.FileNode;
import ecstasy.fs.FileStore;
import ecstasy.fs.FileWatcher;
import ecstasy.fs.Path;

import ecstasy.io.IOException;

/**
 * Native OS FileNode implementation.
 */
const OSFileNode
        implements FileNode
        delegates  Stringable(pathString)
    {
    @Override
    OSFileStore store;

    @Override
    @Lazy Path path.calc()
        {
        return new Path(pathString);
        }

    @Override
    Boolean exists.get() { TODO("native"); }

    @Override
    conditional File linkAsFile()
        {
        return False; // TODO
        }

    @Override
    @Lazy Time created.calc()
        {
        // TODO: should it be the "local" timezone?
        return new Time(createdMillis*TimeOfDay.PICOS_PER_MILLI);
        }

    @Override
    Time modified.get()
        {
        return new Time(modifiedMillis*TimeOfDay.PICOS_PER_MILLI);
        }

    @Override
    @RO Time accessed.get()
        {
        return new Time(accessedMillis*TimeOfDay.PICOS_PER_MILLI);
        }

    @Override
    Boolean readable.get() { TODO("native"); }

    @Override
    Boolean writable.get() { TODO("native"); }

    @Override
    Boolean create()
        {
        return !exists && store.create(this:protected);
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
        return exists && store.delete(this:protected);
        }

    @Override
    conditional FileNode renameTo(String name)
        {
        Path src = path;
        Path dst = new Path(src.parent?, name) : new Path(name);
        try
            {
            return True, store.copyOrMove(src, src.toString(), dst, dst.toString(), move=True);
            }
        catch (IOException e)
            {
            return False;
            }
        }

    @Override
    Int size.get() { TODO("native"); }


    // ----- equality support ----------------------------------------------------------------------

    static <CompileType extends OSFileNode> Int64 hashCode(CompileType value)
        {
        return String.hashCode(value.pathString);
        }

    static <CompileType extends OSFileNode> Boolean equals(CompileType node1, CompileType node2)
        {
        return node1.pathString == node2.pathString &&
               node1.is(OSFile) == node2.is(OSFile);
        }


    // ----- internal ------------------------------------------------------------------------------

    protected String pathString.get() { TODO("native"); }

    private Int createdMillis.get()   { TODO("native"); }
    private Int accessedMillis.get()  { TODO("native"); }
    private Int modifiedMillis.get()  { TODO("native"); }
    }