import ecstasy.fs.FileNode;
import ecstasy.fs.FileWatcher;

import ecstasy.io.IOException;

/**
 * Native OS FileNode implementation.
 */
const OSFileNode
        implements FileNode
        delegates  Stringable(pathString) {

    @Override
    OSFileStore store;

    @Override
    @Lazy Path path.calc() = new Path(pathString);

    @Override
    Boolean exists.get() { TODO("native"); }

    @Override
    conditional File linkAsFile() = store.linkAsFile(this:protected);

    // TODO: should it be the "local" timezone?
    @Override
    @Lazy Time created.calc() = new Time(createdMillis*TimeOfDay.PicosPerMilli);

    @Override
    Time modified.get() = new Time(modifiedMillis*TimeOfDay.PicosPerMilli);

    @Override
    @RO Time accessed.get() = new Time(accessedMillis*TimeOfDay.PicosPerMilli);

    @Override
    Boolean readable.get() { TODO("native"); }

    @Override
    Boolean writable.get() { TODO("native"); }

    @Override
    Boolean create() = !exists && store.create(this:protected);

    @Override
    FileNode ensure() {
        if (!exists) {
            create();
        }
        return this;
    }

    @Override
    Boolean delete() = exists && store.delete(this:protected);

    @Override
    conditional FileNode renameTo(String name) {
        Path src = path;
        Path dst = new Path(src.parent?, name) : new Path(name);
        try {
            return True, store.copyOrMove(src, src.toString(), dst, dst.toString(), move=True);
        } catch (IOException e) {
            return False;
        }
    }

    @Override
    Int size.get() { TODO("native"); }


    // ----- equality support ----------------------------------------------------------------------

    static <CompileType extends OSFileNode> Int64 hashCode(CompileType value) {
        return String.hashCode(value.pathString);
    }

    static <CompileType extends OSFileNode> Boolean equals(CompileType node1, CompileType node2) {
        return node1.pathString == node2.pathString &&
               node1.is(OSFile) == node2.is(OSFile);
    }


    // ----- native --------------------------------------------------------------------------------

    String pathString.get() { TODO("native"); }

    private Int createdMillis.get()   { TODO("native"); }
    private Int accessedMillis.get()  { TODO("native"); }
    private Int modifiedMillis.get()  { TODO("native"); }
}