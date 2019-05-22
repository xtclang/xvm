import Ecstasy.collections.ListMap;

import Ecstasy.fs.AccessDenied;
import Ecstasy.fs.Directory;
import Ecstasy.fs.File;
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

        return contents.keys.iterator();
        }

    @Override
    Iterator<Directory> dirs()
        {
        if (!exists)
            {
            return new Iterator<Directory>()
                {
                @Override
                conditional ElementType next()
                    {
                    return False;
                    }
                };
            }

        TODO
        }

    @Override
    Iterator<File> files()
        {
        if (!exists)
            {
            return new Iterator<File>()
                {
                @Override
                conditional ElementType next()
                    {
                    return False;
                    }
                };
            }

        TODO
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

    @Lazy protected ListMap<String, Object> contents.calc()
        {
        (String[] names, Object[] cookies) = store.loadDirectory(cookie);
        return new ListMap(names, cookies);
        }
    }
