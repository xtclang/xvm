import ecstasy.collections.HashMap;

import ecstasy.fs.Directory;
import ecstasy.fs.File;
import ecstasy.fs.FileNotFound;
import ecstasy.fs.FileStore;
import ecstasy.fs.FileWatcher;
import ecstasy.fs.Path;

/**
 * Native OSStorage service.
 */
service OSStorage
    {
    construct()
        {
        }
    finally
        {
        fileStore = new OSFileStore(this, False);
        }

    @Unassigned
    OSFileStore fileStore;

    FileStore.Cancellable watchFile(Path filePath, FileWatcher watcher)
        {
        if (Directory|File parentDir := find(fileStore, filePath.parent.toString()))
            {
            if (!parentDir.is(Directory))
                {
                throw new FileNotFound(filePath, "No parent directory");
                }

            FileWatcher dirWatcher = new FileWatcher()
                {
                @Override
                Boolean onEvent(Event event, File file)
                    {
                    if (file.name == filePath.name)
                        {
                        // stay asynchronous
                        @Future Boolean cancel = watcher.onEvent(event, file);
                        return cancel;
                        }
                    return False;
                    }
                };

            return watchDir(parentDir.path, dirWatcher);
            }
        else
            {
            throw new FileNotFound(filePath, "No parent directory");
            }
        }

    FileStore.Cancellable watchDir(Path dirPath, FileWatcher watcher)
        {
        String pathString = dirPath.toString();

        FileWatcher?[] watchers;
        Int            index;
        if (watchers := allWatchers.get(pathString))
            {
            index = watchers.size;

            findEmpty:
            for (FileWatcher? w : watchers)
                {
                if (w == Null)
                    {
                    index = findEmpty.count;
                    break;
                    }
                }
            }
        else
            {
            // add the native watch
            watch(pathString);

            watchers = new Array<FileWatcher?>();
            index    = 0;

            allWatchers.put(pathString, watchers);
            }
        watchers[index] = watcher;

        // return () -> removeWatch(pathString, index, watcher);
        return &removeWatch(pathString, index, watcher);
        }

    /**
     * Remove the watcher for the specified directory.
     */
    private void removeWatch(String pathString, Int index, FileWatcher watcher)
        {
        if (FileWatcher?[] watchers := allWatchers.get(pathString))
            {
            if (watchers[index] == watcher)
                {
                watchers[index] = Null;

                // TODO: cleanup if no one watches anymore
                }
            }
        }

    // called natively
    private void onEvent(String pathStringDir, String pathStringNode, Boolean isFile, Int eventId)
        {
        FileWatcher.Event event = FileWatcher.Event.values[eventId];

        if (FileWatcher?[] watchers := allWatchers.get(pathStringDir))
            {
            findWatcher:
            for (FileWatcher? watcher : watchers)
                {
                if (watcher != Null)
                    {
                    @Future Boolean cancel = isFile
                        ? watcher.onEvent(event, fileStore.fileFor(pathStringNode))
                        : watcher.onEvent(event, fileStore.dirFor(pathStringNode));

                    Int index = findWatcher.count;
                    &cancel.whenComplete((cancelled, exception) ->
                        {
                        if (cancelled? || exception != Null)
                            {
                            removeWatch(pathStringDir, index, watcher);
                            }
                        else if (!isFile && event == Created)
                            {
                            // we had a request to watch a directory that has just been created
                            watchDir(new Path(pathStringNode), watcher);
                            }
                        });
                    }
                }
            }
        }

    private Map<String, FileWatcher?[]> allWatchers = new HashMap();


    // ----- used by the native injection logic ----------------------------------------------------

    FileStore store.get()
        {
        return fileStore;
        }

    Directory rootDir.get()
        {
        return fileStore.root;
        }

    // ----- native --------------------------------------------------------------------------------

    @Abstract @RO Directory homeDir;
    @Abstract @RO Directory curDir;
    @Abstract @RO Directory tmpDir;

    conditional Directory|File find(OSFileStore store, String pathString);

    String[] names(String pathString);

    Boolean createDir(String pathString);

    Boolean createFile(String pathString);

    Boolean delete(String pathString);

    void watch(String pathStringDir);
    void unwatch(String pathStringDir);
    }