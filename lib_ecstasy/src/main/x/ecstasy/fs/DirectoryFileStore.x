/**
 * A FileStore implementation that behaves as if the provided directory is actually the root of a
 * new FileStore.
 *
 * This is useful for injecting a FileStore into a Container, because the Container will not be
 * able to see "above" the level of the injected FileStore's "root" directory.
 */
const DirectoryFileStore(Directory origDir, Boolean readOnly = False)
        implements FileStore {
    /**
     * The directory that this FileStore represents.
     */
    protected/private Directory origDir;

    protected FileStore origStore.get() {
        return origDir.store;
    }

    @Lazy Path origPath.calc() {
        return origDir.path.normalize().resolve(Path.ROOT);
    }

    @Override
    @Lazy Directory root.calc() {
        return wrap(origDir);
    }

    @Override
    conditional Directory|File find(Path path) {
        if (Directory|File result := origStore.find(toOrig(path))) {
            return True, wrap(result);
        }

        return False;
    }

    @Override
    Directory dirFor(Path path) {
        return wrap(origStore.dirFor(toOrig(path)));
    }

    @Override
    File fileFor(Path path) {
        return wrap(origStore.fileFor(toOrig(path)));
    }

    @Override
    Directory|File copy(Path source, Path dest) {
        return wrap(origStore.copy(toOrig(source), toOrig(dest)));
    }

    @Override
    Directory|File move(Path source, Path dest) {
        return wrap(origStore.move(toOrig(source), toOrig(dest)));
    }

    @Override
    public/private Boolean readOnly;

    @Override
    FileStore ensureReadOnly() {
        return readOnly ? this : new DirectoryFileStore(origDir, True);
    }

    @Override
    @RO Int capacity.get() {
        return origStore.capacity;
    }

    @Override
    @RO Int bytesUsed.get() {
        return origDir.size;
    }

    @Override
    @RO Int bytesFree.get() {
        return origStore.bytesFree;
    }


    // ----- relative path conversion and file node wrapping ---------------------------------------

    /**
     * Convert a Path that is valid in this FileStore to a Path that will work with the original
     * FileStore.
     *
     * @param path  a path within this FileStore
     *
     * @return the corresponding Path as it would be known in the original FileStore
     */
    protected Path toOrig(Path path) {
        Path base = origPath;
        Path add  = path.normalize();
        return switch (add[0].form) {
            case Root:    add.size > 1 ? base + add[1 ..< add.size] : base;
            case Parent:  assert;
            case Current: assert;
            case Name:    base + add;
        };
    }

    /**
     * Convert a Path that is valid in this original FileStore to a Path that is valid in this
     * FileStore.
     *
     * @param path  a path within the original FileStore
     *
     * @return True iff the specified path denotes a path that exists within this FileStore
     * @return the Path as it would be known in this FileStore
     */
    protected conditional Path fromOrig(Path path) {
        Path basePath = origPath;
        Path fullPath = path.normalize().resolve(Path.ROOT);
        if (fullPath.startsWith(basePath)) {
            Int baseSize = basePath.size;
            Int fullSize = fullPath.size;
            return True, fullSize == baseSize
                    ? Path.ROOT
                    : fullPath[baseSize ..< fullSize].resolve(Path.ROOT);
        }

        return False;
    }

    /**
     * Create a File or Directory wrapper around the provided File or Directory node from the
     * original FileStore.
     *
     * @param origNode  a File or Directory node from the original FileStore
     *
     * @return a corresponding File or Directory node from this DirectoryFileStore that prevents
     *         leaks of information outside of the original directory [origDir], and respects the
     *         [readOnly] setting of the DirectoryFileStore
     */
    protected <Node extends File|Directory> Node wrap(Node origNode) {
        if (origNode.is(File)) {
            File file = new FileWrapper(origNode);
            return &file.maskAs(File).as(Node);
        }

        Directory dir = new DirectoryWrapper(origNode);
        return &dir.maskAs(Directory).as(Node);
    }


    // ----- file node wrappers --------------------------------------------------------------------

    /**
     * Represents a FileNode within this DirectoryFileStore that corresponds to a FileNode in the
     * original FileStore.
     */
    const FileNodeWrapper(FileNode origNode)
            delegates FileNode(origNode) {
        /**
         * True iff this file node is the root directory of the DirectoryFileStore.
         */
        protected Boolean isRoot.get() {
            return origNode.path == origDir.path;
        }

        /**
         * Verify that the directory can be written to.
         *
         * @return True if the check passes
         *
         * @throws AccessDenied if the DirectoryFileStore is ReadOnly
         */
        protected Boolean checkWritable() {
            return !readOnly || throw new AccessDenied();
        }

        @Override
        @RO FileStore store.get() {
            return this.DirectoryFileStore;
        }

        @Override
        @RO Directory? parent.get() {
            return isRoot
                    ? Null
                    : wrap(origNode.parent? : assert);
        }

        @Override
        @Lazy Path path.calc() {
            assert Path path := fromOrig(origNode.path);
            return path;
        }

        @Override
        @RO String name.get() {
            return isRoot ? "" : origNode.name;
        }

        @Override
        conditional File linkAsFile() {
            if (File origLink := origNode.linkAsFile()) {
                assert fromOrig(origLink.path);
                return True, wrap(origLink);
            }

            return False;
        }

        @Override
        @RO Boolean writable.get() {
            return !readOnly && origNode.writable;
        }

        @Override
        Boolean create() {
            return checkWritable() && origNode.create();
        }

        @Override
        FileNode ensure() {
            assert exists || create();
            return this;
        }

        @Override
        Boolean delete() {
            return checkWritable() && origNode.delete();
        }

        @Override
        conditional FileNode renameTo(String name) {
            if (checkWritable(), FileNode result := origNode.renameTo(name)) {
                return True, &result == &origNode ? this : wrap(result.as(Directory|File));
            }

            return False;
        }

        @Override
        Cancellable watch(FileWatcher watcher) {
            return origNode.watch(new FileWatcherWrapper(watcher));
        }
    }

    /**
     * A Directory implementation that represents itself in relation to the DirectoryFileStore,
     * while delegating its functionality to the corresponding Directory in the original FileStore.
     */
    const DirectoryWrapper
            extends FileNodeWrapper
            implements Directory {

        construct(Directory origDir) {
            construct FileNodeWrapper(origDir);
        }

        protected Directory origDir.get() {
            return origNode.as(Directory);
        }

        @Override
        Iterator<String> names() {
            return origDir.names();
        }

        @Override
        Iterator<Directory> dirs() {
            return new Iterator<Directory>() {
                Iterator<Directory> origIter = origDir.dirs();

                @Override
                conditional Directory next() {
                    if (Directory origNext := origIter.next()) {
                        return True, wrap(origNext);
                    }

                    return False;
                }
            };
        }

        @Override
        Iterator<File> files() {
            return new Iterator<File>() {
                Iterator<File> origIter = origDir.files();

                @Override
                conditional File next() {
                    if (File origNext := origIter.next()) {
                        return True, wrap(origNext);
                    }

                    return False;
                }
            };
        }

        @Override
        Iterator<File> filesRecursively() {
            return new Iterator<File>() {
                Iterator<File> origIter = origDir.filesRecursively();

                @Override
                conditional File next() {
                    if (File origNext := origIter.next()) {
                        return True, wrap(origNext);
                    }

                    return False;
                }
            };
        }

        @Override
        conditional Directory|File find(String name) {
            if (Directory|File result := origDir.find(name)) {
                return True, wrap(result);
            }

            return False;
        }

        @Override
        Directory dirFor(String name) {
            return wrap(origDir.dirFor(name));
        }

        @Override
        File fileFor(String name) {
            return wrap(origDir.fileFor(name));
        }

        @Override
        Boolean deleteRecursively() {
            return checkWritable() && origDir.deleteRecursively();
        }

        @Override
        Cancellable watchRecursively(FileWatcher watcher) {
            return origDir.watchRecursively(new FileWatcherWrapper(watcher));
        }
    }

    /**
     * A File implementation that represents itself in relation to the DirectoryFileStore, while
     * delegating its functionality to the corresponding File in the original FileStore.
     */
    const FileWrapper
            extends FileNodeWrapper
            implements File {
        construct(File origFile) {
            construct FileNodeWrapper(origFile);
        }

        protected File origFile.get() {
            return origNode.as(File);
        }

        @Override
        Byte[] contents {
            @Override
            Byte[] get() {
                return origFile.contents;
            }

            @Override
            void set(Byte[] bytes) {
                checkWritable();
                origFile.contents = bytes;
            }
        }

        @Override
        Byte[] read(Range<Int> range) {
            return origFile.read(range);
        }

        @Override
        File truncate(Int newSize = 0) {
            checkWritable();
            origFile.truncate(newSize);
            return this;
        }

        @Override
        File append(Byte[] contents) {
            checkWritable();
            origFile.append(contents);
            return this;
        }

        @Override
        conditional FileStore openArchive() {
            if (FileStore result := origFile.openArchive()) {
                return True, readOnly && !result.readOnly
                        ? result.ensureReadOnly()
                        : result;
            }

            return False;
        }

        @Override
        FileChannel open(ReadOption read=Read, WriteOption[] write = NoWrite) {
            if (!write.empty) {
                checkWritable();
            }
            return origFile.open(read, write);
        }
    }

    /**
     * A FileWatcher implementation that represents itself in relation to the original FileStore,
     * while delegating its functionality to the provided FileWatcher that operates in relation to
     * the DirectoryFileStore.
     */
    const FileWatcherWrapper(FileWatcher origWatcher)
            delegates FileWatcher(origWatcher) {

        @Override
        Boolean onEvent(Event event, Directory dir) {
            return fromOrig(dir.path) && origWatcher.onEvent(event, wrap(dir));
        }

        @Override
        Boolean onEvent(Event event, File file) {
            return fromOrig(file.path) && origWatcher.onEvent(event, wrap(file));
        }
    }
}