import Ecstasy.collections.ListMap;

import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
import Ecstasy.fs.FileNode;
import Ecstasy.fs.FileWatcher;
import Ecstasy.fs.Path;

/**
 * Constant Pool Directory implementation.
 */
const CPDirectory(CPFileStore:protected store, Object cookie, Path path, DateTime created, DateTime modified, Int size)
        extends CPFileNode(store, cookie, path, created, modified, size)
        implements Directory
    {
    @Override
    Iterator<String> names()
        {
        if (!exists)
            {
            return new Iterator<String>()
                {
                @Override
                conditional ElementType next()
                    {
                    return False;
                    }
                };
            }

DEBUG;
        return contents.keys.iterator();
        }

    @Override
    Iterator<Directory> dirs()
        {
        return new Iterator<Directory>()
            {
            Iterator<FileNode>? iter = exists ? contents.values.iterator(node -> node.is(Directory)) : Null;

            @Override
            conditional Directory next() // TODO GG "ElementType> did not work here (or below in files())
                {
                if (iter != null)
                    {
                    if (FileNode node : iter?.next())
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
            Iterator<FileNode>? iter = exists ? contents.values.iterator(node -> node.is(File)) : Null;

            @Override
            conditional File next()
                {
                if (iter != null)
                    {
                    if (FileNode node : iter?.next())
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

        return True, new CPDirectory(
                store,
                cookie,
                path + name,
                DateTime.EPOCH, // TODO new DateTime(created, TimeZone.UTC),
                DateTime.EPOCH, // TODO new DateTime(modified, TimeZone.UTC),
                1);             // TODO
        }

    @Override
    Directory dirFor(String name)
        {
        // TODO
        return store.dirFor(path + name);
        }

    @Override
    File fileFor(String name)
        {
        // TODO
        return store.fileFor(path + name);
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
        (String[] names, Object[] cookies) = store.loadDirectory(cookie);
        Int count = names.size;
        var nodes = new Array<CPDirectory|CPFile>(count);
        for (Int i = 0; i < count; ++i)
            {
            Object cookie = cookies[i];
            (Boolean isdir, String name, String created, String modified, Int size) = store.loadNode(cookie);
            nodes[i] = isdir
                    ? new CPDirectory(store, cookie, path + name, DateTime.EPOCH, DateTime.EPOCH, size)    // TODO date/times
                    : new CPFile(store, cookie, path + name, DateTime.EPOCH, DateTime.EPOCH, size);
            }
        return new ListMap(names, nodes);
        }
    }
