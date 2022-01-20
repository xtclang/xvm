import ecstasy.collections.ListMap;

import ecstasy.fs.AccessDenied;
import ecstasy.fs.Directory;
import ecstasy.fs.File;
import ecstasy.fs.FileNode;
import ecstasy.fs.FileWatcher;
import ecstasy.fs.Path;

/**
 * Constant Pool Directory implementation.
 */
const CPDirectory(Object cookie, CPFileStore? fileStore, Path path, DateTime created, DateTime modified, Int size)
        extends CPFileNode(cookie, fileStore, path, created, modified, size)
        implements Directory
    {
    construct (Object cookie)
        {
        construct CPFileNode(cookie);
        }

    @Override
    Iterator<String> names()
        {
        if (!exists)
            {
            return new Iterator<String>()
                {
                @Override
                conditional String next()
                    {
                    return False;
                    }
                };
            }

        return contents.keys.iterator();
        }

    @Override
    Iterator<Directory> dirs()
        {
        return new Iterator<Directory>()
            {
            Iterator<FileNode>? iter = exists ? contents.values.iterator().filter(node -> node.is(Directory)) : Null;

            @Override
            conditional Directory next()
                {
                if (iter != Null)
                    {
                    if (FileNode node := iter?.next())
                        {
                        return True, node.as(Directory);
                        }
                    }

                return False;
                }
            };
        }

    @Override
    Iterator<File> files()
        {
        return new Iterator<File>()
            {
            Iterator<FileNode>? iter = exists ? contents.values.iterator().filter(node -> node.is(File)) : Null;

            @Override
            conditional File next()
                {
                if (iter != Null)
                    {
                    if (FileNode node := iter?.next())
                        {
                        return True, node.as(File);
                        }
                    }

                return False;
                }
            };
        }

    @Override
    Iterator<File> filesRecursively()
        {
        if (!exists)
            {
            return files(); // an empty iterator
            }

        TODO
        }

    @Override
    conditional Directory|File find(String name)
        {
        if (!exists)
            {
            return False;
            }

        Object cookie = contents.get(name);
        assert cookie != Null;

        return True, new CPDirectory(cookie, fileStore, path + name, created, modified, 1);
        }

    @Override
    Directory dirFor(String name)
        {
        TODO
        // TODO not this: return store.dirFor(path + name);
        }

    @Override
    File fileFor(String name)
        {
        TODO
        // TODO not this: return store.fileFor(path + name);
        }

    @Override
    Boolean deleteRecursively()
        {
        if (!exists)
            {
            return False;
            }
        throw new AccessDenied();
        }

    @Override
    Cancellable watchRecursively(FileWatcher watch)
        {
        return () -> {};
        }


    // ----- native support ------------------------------------------------------------------------

    @Lazy protected ListMap<String, CPDirectory|CPFile> contents.calc()
        {
        (String[] names, Object[] cookies) = CPFileStore.loadDirectory(cookie);
        Int count = names.size;
        var nodes = new Array<CPDirectory|CPFile>(count);
        for (Int i = 0; i < count; ++i)
            {
            Object cookie = cookies[i];
            (Boolean isdir, String name, DateTime created, DateTime modified, Int size) = CPFileStore.loadNode(cookie);
            nodes[i] = isdir
                    ? new CPDirectory(cookie, fileStore, path + name, created, modified, size)
                    : new CPFile(cookie, fileStore, path + name, created, modified, size);
            }
        return new ListMap(names, nodes);
        }
    }
