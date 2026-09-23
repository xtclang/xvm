import ecstasy.fs.FileNotFound;
import ecstasy.fs.FileWatcher;

/**
 * Native OSStorage service.
 */
service OSStorage {
    construct() {} finally {
        fileStore = new OSFileStore(this, False);
    }

    @Unassigned
    OSFileStore fileStore;

    FileStore.Cancellable watchFile(Path filePath, FileWatcher watcher) {
        if (Directory|File parentDir := find(fileStore, filePath.parent.toString())) {
            if (!parentDir.is(Directory)) {
                throw new FileNotFound(filePath, "No parent directory");
            }

            FileWatcher dirWatcher = new FileWatcher() {
                @Override
                Boolean onEvent(Event event, File file) {
                    if (file.name == filePath.name) {
                        // stay asynchronous
                        return watcher.onEvent^(event, file);
                    }
                    return False;
                }
            };

            return watchDir(parentDir.path, dirWatcher);
        } else {
            throw new FileNotFound(filePath, "No parent directory");
        }
    }

    FileStore.Cancellable watchDir(Path dirPath, FileWatcher watcher) {
        Int id = watch(dirPath.toString(), watcher);
        return &unwatch(id);
    }

    // Called natively for an individual, request-owned subscription.
    private void onEvent(String pathStringDir, String pathStringNode, Boolean isFile, Int eventId,
                         Int id) {
        if (FileWatcher watcher := lookupWatch(id)) {
            FileWatcher.Event event = FileWatcher.Event.values[eventId];
            @Future Boolean cancel = isFile
                ? watcher.onEvent(event, fileStore.fileFor(pathStringNode))
                : watcher.onEvent(event, fileStore.dirFor(pathStringNode));
            &cancel.whenComplete((cancelled, exception) -> {
                if (cancelled? || exception != Null) {
                    unwatch(id);
                }
            });
        }
    }

    @Override
    String toString() = "Storage";

    // ----- used by the native injection logic ----------------------------------------------------

    FileStore store.get() = fileStore;

    Directory rootDir.get() = fileStore.root;

    // ----- native --------------------------------------------------------------------------------

    @Abstract @RO Directory homeDir;
    @Abstract @RO Directory curDir;
    @Abstract @RO Directory tmpDir;

    conditional Directory|File find(OSFileStore store, String pathString) {TODO("Native");}

    String[] names(String pathString)      {TODO("Native");}
    Boolean  createDir(String pathString)  {TODO("Native");}
    Boolean  createFile(String pathString) {TODO("Native");}
    Boolean  delete(String pathString)     {TODO("Native");}
    Int      watch(String pathStringDir, FileWatcher watcher) {TODO("Native");}
    void     unwatch(Int id) {TODO("Native");}
    conditional FileWatcher lookupWatch(Int id) {TODO("Native");}

    static OSStorage instance() {TODO("Native");}
}
