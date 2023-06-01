/**
 * FileWatcher represents an event interface for an object that is watching for changes occurring
 * to files and/or directories within a FileStore.
 */
interface FileWatcher {
    enum Subject {DirOnly, FileOnly, Both}

    enum Event {Created, Modified, Deleted}

    static Set<Event> ALL_EVENTS = Set:[Created, Modified, Deleted];

    /**
     * If the mechanism that is detecting and reporting the changes to this FileWatcher is capable
     * of selectively delivering events, then it may ask this FileWatcher for the desired subjects
     * of detection and/or notification.
     *
     * This value is considered to be _advisory_; the FileWatcher must be written in a manner that
     * ignores events that it does not desire.
     */
    @RO Subject desiredSubject.get() {
        return Both;
    }

    /**
     * If the mechanism that is detecting and reporting the changes to this FileWatcher is capable
     * of selectively delivering events, then it may ask this FileWatcher for the desired events
     * of detection and/or notification.
     *
     * This value is considered to be _advisory_; the FileWatcher must be written in a manner that
     * ignores events that it does not desire.
     */
    @RO Set<Event> desiredEvents.get() {
        return ALL_EVENTS;
    }

    /**
     * If the mechanism that is detecting and reporting the changes to this FileWatcher is capable
     * of modulating the immediacy of detecting changes and/or the frequency of notifying this
     * FileWatcher of those changes, then it may ask this FileWatcher for a desired period of
     * detection and/or notification. The desired period may then be used to speed up or slow down
     * the aggressiveness of change detection and/or the delivery of the corresponding events.
     *
     * This value is considered to be _advisory_, and not definitive; the FileWatcher must be
     * written in a manner that is tolerant of event delivery delays, or conversely, of events
     * being delivered at a higher-than-desired rate.
     */
    @RO Duration desiredPeriod.get() {
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
    Boolean onEvent(Event event, Directory dir) {
        return False;
    }

    /**
     * Invoked when a file event has been detected.
     *
     * @param file   the file that was created, modified, or deleted
     * @param event  Created, Modified, or Deleted
     *
     * @return True to cancel the watch that caused this event to be delivered
     */
    Boolean onEvent(Event event, File file) {
        return False;
    }

    /**
     * Invoked when events scheduled to be delivered to this FileWatcher backed up to the point
     * that at least one event was discarded without being delivered. The number of discarded events
     * is indeterminable.
     *
     * @return True to attempt to cancel the watch(es) that caused the back-up
     */
    Boolean eventsDiscarded() {
        return False;
    }
}
