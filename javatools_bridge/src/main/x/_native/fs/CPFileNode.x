import ecstasy.fs.AccessDenied;
import ecstasy.fs.FileNode;
import ecstasy.fs.FileWatcher;

/**
 * Constant Pool FileNode implementation.
 */
const CPFileNode(Object cookie, FileStore? fileStore, Path path, Time created, Time modified, Int size)
        implements FileNode
        delegates  Stringable(path) {

    construct(Object cookie) {
        (Boolean isDir, String name, Time created, Time modified, Int size) =
                CPFileStore.loadNode(cookie);
        construct CPFileNode(cookie, fileStore, new Path(name), created, modified, size);
    }

    @Override
    FileStore store.get() = fileStore ?: throw new IllegalState("standalone resource");

    @Override
    @RO String name.get() = path.form == Root ? "" : path.name;

    @Override
    @RO Boolean exists.get() = cookie != Null;

    @Override
    conditional File linkAsFile() = False; // no links on the Constant Pool

    @Override
    @RO Time accessed.get() = Time.EPOCH;

    @Override
    @RO Boolean readable.get() = True;

    @Override
    @RO Boolean writable.get() = False;

    @Override
    Boolean create() = exists ? False : throw new AccessDenied();

    @Override
    FileNode ensure() = exists ? this : throw new AccessDenied();

    @Override
    Boolean delete() = exists ? throw new AccessDenied() : False;

    @Override
    conditional FileNode renameTo(String name) = False;

    @Override
    Cancellable watch(FileWatcher watch)  = () -> {};


    // ----- native support ------------------------------------------------------------------------

    /**
     * The native handle for the constant which this node represents.
     */
    protected Object cookie;
}