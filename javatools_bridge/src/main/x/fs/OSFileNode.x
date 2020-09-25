import ecstasy.fs.Directory;
import ecstasy.fs.File;
import ecstasy.fs.FileNode;
import ecstasy.fs.FileStore;
import ecstasy.fs.FileWatcher;
import ecstasy.fs.Path;

/**
 * Native OS FileNode implementation.
 */
const OSFileNode
        implements FileNode
        delegates  Stringable(pathString)
    {
    @Override
    @Lazy Path path.calc()
        {
        return new Path(pathString);
        }

    @Override
    String name.get()
        {
        return path.form == Root ? "" : path.name;
        }

    @Override
    Boolean exists.get() { TODO("native"); }

    @Override
    conditional File linkAsFile()
        {
        return False; // TODO
        }

    @Override
    @Lazy DateTime created.calc()
        {
        // TODO: should it be the "local" timezone?
        return new DateTime(createdMillis*Time.PICOS_PER_MILLI);
        }

    @Override
    DateTime modified.get()
        {
        return new DateTime(modifiedMillis*Time.PICOS_PER_MILLI);
        }

    @Override
    @RO DateTime accessed.get()
        {
        return new DateTime(accessedMillis*Time.PICOS_PER_MILLI);
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
    conditional FileNode renameTo(String name);


    // ----- equality support ----------------------------------------------------------------------

    static <CompileType extends OSFileNode> Int hashCode(CompileType value)
        {
        return String.hashCode(value.pathString);
        }

    static <CompileType extends OSFileNode> Boolean equals(CompileType node1, CompileType node2)
        {
        return node1.pathString == node2.pathString &&
               node1.is(OSFile) == node2.is(OSFile);
        }


    // ----- native --------------------------------------------------------------------------------

    @Unassigned protected OSFileStore store;

    @Override Int size.get()          { TODO("native"); }

    protected String pathString.get() { TODO("native"); }

    private Int createdMillis.get()   { TODO("native"); }
    private Int accessedMillis.get()  { TODO("native"); }
    private Int modifiedMillis.get()  { TODO("native"); }
    }
