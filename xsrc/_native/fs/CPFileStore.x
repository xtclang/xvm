import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
import Ecstasy.fs.FileStore;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Constant Pool FileStore implementation.
 */
const CPFileStore(Object constRoot)
        implements FileStore
    {
    @Override
    @Lazy @RO Directory root.calc()
        {
        (Boolean isdir, String name, UInt128 created, UInt128 modified, Int size) = loadNode(constRoot);
        assert isdir;
        return new CPDirectory(
                this,
                constRoot,
                Path.ROOT,
                new DateTime(created, TimeZone.UTC),
                new DateTime(modified, TimeZone.UTC),
                size);
        }

    @Override
    conditional Directory|File find(Path path)
        {
        path = path.normalize();
        if (path.depth < 0)
            {
            // can't climb up past the root
            return False;
            }

        Directory dir = root;
        Segments: for (Path segment : path)
            {
            switch (segment.form)
                {
                case Root:
                    // this should only occur at the first segment
                    assert Segments.first && dir == root;
                    break;

                case Parent:
                    // since this was not cleaned out by the call to normalize(), it implies that
                    // the caller is trying to climb up past the root
                    return False;

                case Current:
                    // this should have been cleaned out by the call to normalize()
                    assert;

                case Name:
                    if (Directory|File node : dir.find(segment.name))
                        {
                        if (Segments.last)
                            {
                            return True, node;
                            }

                        if (node.is(File))
                            {
                            // this isn't the last segment, and the node isn't a directory, so we
                            // can't keep searching down the tree
                            return False;
                            }

                        dir = node;
                        }
                    else
                        {
                        return False;
                        }
                    break;
                }
            }
        assert;
        }

    @Override
    Directory dirFor(Path path)
        {
        if (Directory|File node : find(path))
            {
            if (node.is(Directory))
                {
                return node;
                }
            }

        return new CPDirectory(this, Null, path, DateTime.EPOCH, DateTime.EPOCH, 0);
        }

    @Override
    File fileFor(Path path)
        {
        if (Directory|File node : find(path))
            {
            if (node.is(File))
                {
                return node;
                }
            }

        return new CPFile(this, Null, path, DateTime.EPOCH, DateTime.EPOCH, 0);
        }

    @Override
    Directory|File copy(Path source, Path dest)
        {
        throw new AccessDenied();
        }

    @Override
    Directory|File move(Path source, Path dest)
        {
        throw new AccessDenied();
        }

    @Override
    Cancellable watch(Path path, FileWatcher watch)
        {
        return () -> {};
        }

    @Override
    Cancellable watchRecursively(Path path, FileWatcher watch)
        {
        return () -> {};
        }

    @Override
    @RO Boolean readOnly.get()
        {
        return True;
        }

    @Override
    FileStore ensureReadOnly()
        {
        return this;
        }

    @Override
    @RO Int capacity.get()
        {
        return root.size;
        }

    @Override
    @RO Int bytesUsed.get()
        {
        return root.size;
        }

    @Override
    @RO Int bytesFree.get()
        {
        return 0;
        }


    // ----- native support ------------------------------------------------------------------------

    /**
     * The handle for the root directory constant.
     */
    protected Object constRoot;

    /**
     * Load meta-data for a node.
     */
    protected static (Boolean isdir, String name, UInt128 created, UInt128 modified, Int size) loadNode(Object constNode);

    /**
     * Load contents for a directory.
     */
    protected static (String[] names, Object[] cookies) loadDirectory(Object constNode);

    /**
     * Load contents for a file.
     */
    protected static immutable Byte[] loadFile(Object constNode);
    }
