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
const CPDirectory(Object cookie, FileStore? fileStore, Path path, Time created, Time modified, Int size)
        extends CPFileNode(cookie, fileStore, path, created, modified, size)
        implements Directory {

    construct(Object cookie) {
        construct CPFileNode(cookie);

        fileStore = new CPFileStore(path.name, cookie);
        path      = ROOT;
    }

    @Override
    Iterator<String> names() {
        if (!exists) {
            return new Iterator<String>() {
                @Override
                conditional String next() {
                    return False;
                }
            };
        }

        return contents.keys.iterator();
    }

    @Override
    Iterator<Directory> dirs() {
        return new Iterator<Directory>() {
            Iterator<FileNode>? iter = exists ? contents.values.iterator().filter(node -> node.is(Directory)) : Null;

            @Override
            conditional Directory next() {
                if (FileNode node := iter?.next()) {
                    return True, node.as(Directory);
                }

                return False;
            }
        };
    }

    @Override
    Iterator<File> files() {
        return new Iterator<File>() {
            Iterator<FileNode>? iter = exists ? contents.values.iterator().filter(node -> node.is(File)) : Null;

            @Override
            conditional File next() {
                if (FileNode node := iter?.next()) {
                    return True, node.as(File);
                }

                return False;
            }
        };
    }

    @Override
    conditional Directory|File find(String name) {
        return name.size == 0
                ? (True, this)
                : name.indexOf('/')
                    ? store.find(path + name)
                    : contents.get(name);
    }

    @Override
    Directory dirFor(String name) {
        if (Directory|File node := find(name), node.is(Directory)) {
            return node;
        }
        throw new AccessDenied();
    }

    @Override
    File fileFor(String name) {
        if (Directory|File node := find(name), node.is(File)) {
            return node;
        }
        throw new AccessDenied();
    }

    @Override
    Boolean deleteRecursively() = exists ? throw new AccessDenied() : False;

    @Override
    Cancellable watchRecursively(FileWatcher watch) = () -> {};


    // ----- native support ------------------------------------------------------------------------

    @Lazy protected ListMap<String, CPDirectory|CPFile> contents.calc() {
        (String[] names, Object[] cookies) = CPFileStore.loadDirectory(cookie);
        Int count = names.size;
        var nodes = new Array<CPDirectory|CPFile>(count);
        for (Int i = 0; i < count; ++i) {
            Object cookie = cookies[i];
            (Boolean isDir, String name, Time created, Time modified, Int size) = CPFileStore.loadNode(cookie);
            nodes[i] = isDir
                    ? new CPDirectory(cookie, fileStore, path + name, created, modified, size)
                    : new CPFile(cookie, fileStore, path + name, created, modified, size);
        }
        return new ListMap(names, nodes);
    }
}