import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.File;
import Ecstasy.fs.FileChannel;
import Ecstasy.fs.FileNotFound;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.Path;

/**
 * Constant Pool File implementation.
 */
const CPFile(Object cookie, Path path, DateTime created, DateTime modified, Int size)
        extends CPFileNode(cookie, path, created, modified, size)
        implements File
    {
    construct (Object cookie)
        {
        construct CPFileNode(cookie);
        }

    @Override
    @Lazy immutable Byte[] contents.calc()
        {
        if (!exists)
            {
            throw new FileNotFound();
            }

        return CPFileStore.loadFile(cookie);
        }

    @Override
    File truncate(Int newSize = 0)
        {
        throw exists ? new AccessDenied() : new FileNotFound();
        }

    @Override
    conditional FileStore openArchive()
        {
        // TODO eventually
        return False;
        }

    @Override
    FileChannel open(ReadOption read=Read, WriteOption... write=[Write])
        {
        TODO
        }
    }
