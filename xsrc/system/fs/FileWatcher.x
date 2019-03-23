/**
 * FileWatcher represents an event interface for an object that is watching for changes occurring
 * to files and/or directories within a FileStore.
 */
interface FileWatcher
    {
    enum Subject {DirOnly, FileOnly, Both}

    enum Event {Created, Modified, Deleted}

    /**
     * If the mechanism that is detecting and reporting the changes to this FileWatcher is capable
     * of selectively delivering events, then it may ask this FileWatcher for the desired subjects
     * of detection and/or notification.
     *
     * This value is considered to be _advisory_; The FileWatcher must be written in a manner that
     * ignores events that it does not desire.
     */
    @RO Subject desiredSubject.get()
        {
        return Both;
        }

    /**
     * If the mechanism that is detecting and reporting the changes to this FileWatcher is capable
     * of selectively delivering events, then it may ask this FileWatcher for the desired events
     * of detection and/or notification.
     *
     * This value is considered to be _advisory_; The FileWatcher must be written in a manner that
     * ignores events that it does not desire.
     */
    @RO Set<Event> desiredEvents.get()
        {
        // REVIEW GG why do I need "Event." for these? ;-)
        return new collections.ListSet([Event.Created, Event.Modified, Event.Deleted]);
        }

    /**
     * If the mechanism that is detecting and reporting the changes to this FileWatcher is capable
     * of modulating the immediacy of detecting changes and/or the frequency of notifying this
     * FileWatcher of those changes, then it may ask this FileWatcher for a desired period of
     * detection and/or notification. The desired period may then be used to speed up or slow down
     * the aggressiveness of change detection and/or the delivery of the corresponding events.
     *
     * This value is considered to be _advisory_, and not definitive.
     */
    @RO Duration desiredPeriod.get()
        {
        return Duration.ofSeconds(10);
        }

    /**
     * Invoked when a directory event has been detected.
     *
     * @param dir    the directory that was created or deleted
     * @param event  either Created or Deleted
     *
     * @return True to cancel the watch that caused this event to be delivered
     */
    Boolean dir(Directory dir, Event event)
        {
        return false;
        }

    /**
     * Invoked when a file even has been detected.
     *
     * @param file   the file that was created, modified, or deleted
     * @param event  Created, Modified, or Deleted
     *
     * @return True to cancel the watch that caused this event to be delivered
     */
    Boolean file(File file, Event event)
        {
        return false;
        }

    /**
     * Invoked when events scheduled to be delivered to this FileWatcher backed up to the point
     * that at least one event was discarded without being delivered. The number of discarded events
     * is indeterminable.
     *
     * @return True to attempt to cancel the watch(es) that caused the back-up
     */
    Boolean eventsDiscarded()
        {
        return false;
        }
    }
