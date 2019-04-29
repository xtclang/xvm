/**
 * Native OS File implementation.
 */
class OSFile
        implements fs.File
    {
    @Override
    @RO Path path
        {
        TODO
        }

    @Override
        {
        TODO
        }

    @Override
    @RO String name
        {
        TODO
        }

    @Override
    @RO Boolean exists
        {
        TODO
        }

    @Override
    @RO DateTime created
        {
        TODO
        }

    @Override
    DateTime modified
        {
        TODO
        }

    @Override
    @RO DateTime accessed
        {
        TODO
        }

    @Override
    @RO Boolean readable
        {
        TODO
        }

    @Override
    @RO Boolean writable
        {
        TODO
        }

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
