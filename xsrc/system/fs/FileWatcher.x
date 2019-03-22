/**
 * FileWatcher represents an event interface for an object that is watching for changes occurring
 * to files and/or directories within a FileStore.
 */
interface FileWatcher
    {
    /**
     * If the mechanism that is detecting and reporting the changes to this FileWatcher is capable
     * of modulating the immediacy of detecting changes and/or the frequency of notifying this
     * FileWatcher of those changes, then it may ask this FileWatcher for a desired period of
     * detection and/or notification. The desired period may then be used to speed up or slow down
     * the aggressiveness of change detection and/or the delivery of the corresponding events.
     */
    @RO Duration desiredPeriod;

    /**
     * Invoked when a directory creation has been detected.
     *
     * @param the directory that was created
     *
     * @return True to cancel the watch that caused this event to be delivered
     */
    Boolean directoryCreated(Directory dir);

    /**
     * Invoked when a directory deletion has been detected.
     *
     * @param the directory that was deleted
     *
     * @return True to cancel the watch that caused this event to be delivered
     */
    Boolean directoryDeleted(Directory dir);
    
    /**
     * Invoked when a file creation has been detected.
     *
     * @param the file that was created
     *
     * @return True to cancel the watch that caused this event to be delivered
     */
    Boolean fileCreated(File file);

    /**
     * Invoked when a file deletion has been detected.
     *
     * @param the file that was deleted
     *
     * @return True to cancel the watch that caused this event to be delivered
     */
    Boolean fileDeleted(File file);

    /**
     * Invoked when a file modification has been detected.
     *
     * @param the file that was modified
     *
     * @return True to cancel the watch that caused this event to be delivered
     */
    Boolean fileModified(File file);
    
    /**
     * Invoked when events scheduled to be delivered to this FileWatcher backed up to the point
     * that at least one event was discarded without being delivered. The number of discarded events
     * is indeterminable.
     *
     * @return True to attempt to cancel the watch(es) that caused the back-up
     */
    Boolean notificationsDiscarded();
    }
