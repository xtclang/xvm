import ecstasy.fs.AccessDenied;
import ecstasy.fs.File;
import ecstasy.fs.FileChannel;
import ecstasy.fs.FileNotFound;
import ecstasy.fs.FileStore;
import ecstasy.fs.Path;

/**
 * Constant Pool File implementation.
 */
const CPFile(Object cookie, FileStore? fileStore, Path path, Time created, Time modified, Int size)
        extends CPFileNode(cookie, fileStore, path, created, modified, size)
        implements File {
    construct(Object cookie) {
        construct CPFileNode(cookie);
    }

    @Override
    @Lazy immutable Byte[] contents.calc() {
        if (!exists) {
            throw new FileNotFound(path);
        }

        return CPFileStore.loadFile(cookie);
    }

    @Override
    immutable Byte[] read(Range<Int> range) = contents.slice(range);

    @Override
    File truncate(Int newSize = 0) = throw exists ? new AccessDenied() : new FileNotFound(path);

    @Override
    File append(Byte[] contents) = throw new AccessDenied();

    @Override
    conditional FileStore openArchive() {
        // TODO eventually
        return False;
    }

    @Override
    FileChannel open(ReadOption read=Read, WriteOption[] write=[Write]) {
        TODO
    }
}