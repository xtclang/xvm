import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.File;
import Ecstasy.fs.FileChannel;
import Ecstasy.fs.FileNotFound;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.Path;

/**
 * Constant Pool File implementation.
 */
const CPFile(CPFileStore store, Path path, DateTime created, DateTime modified, Boolean exists)
        extends CPFileNode(store, path, created, modified, exists)
        implements File
    {
    @Override
    immutable Byte[] contents.get()
        {
        if (!exists)
            {
            throw new FileNotFound();
            }

        TODO
        }

    @Override
    File truncate(Int newSize = 0)
        {
        if (exists)
            {
            throw new AccessDenied();
            }
        else
            {
            throw new FileNotFound();
            }
        }

    @Override
    conditional FileStore archive()
        {
        // TODO eventually
        return False;
        }

    @Override
    FileChannel open(ReadOption read=Read, WriteOption... write=[Write])
        {
        TODO
        }

    @Override
    @RO Int size.get()
        {
        return contents.size;
        }
    }
