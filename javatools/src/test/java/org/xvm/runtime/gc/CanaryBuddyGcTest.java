package org.xvm.runtime.gc;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import org.xvm.util.ShallowSizeOf;

import static java.lang.Math.max;
import static java.lang.System.currentTimeMillis;

class CanaryBuddyGcTest {
    public static void out(Object o) {
        System.out.println(o);
    }

    static final boolean RECLAIM = true;
    static final long RAM_SIZE = 100_000_000L;
    static final long TEST_ITERS = 100_000_000L;
    static final int CANARY_SIZE = 64 * 1024;

    static CollectibleList<C> list = null;

    static final long REF_SIZE = ShallowSizeOf.instanceOf(Reclaim.class);
    static final long OBJ_SIZE = ShallowSizeOf.instanceOf(C.class);
    static final long LIST_SIZE = ShallowSizeOf.instanceOf(CollectibleList.class) + ShallowSizeOf.instanceOf(ArrayList.class) + ShallowSizeOf.arrayOf(C.class, 16);

    @SuppressWarnings("fallthrough")
    public static void main(String[] args) {
        // we'll need multiple threads
        // with each thread cycling through different container contexts

        // but let's start with a simple test: alloc in a loop
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Xvm xvm = new Xvm(RAM_SIZE);
        Container c1 = xvm.createContainer();
        Ctx ctx = new Ctx(c1);

//        ReferenceQueue q = new ReferenceQueue();
//        Thread thread = Thread.currentThread();
//        ScopedValue<ReferenceQueue> RefQueue = ScopedValue.newInstance();
//        ScopedValue.where(RefQueue, q).run(() -> {
//        });

        long startTime = System.currentTimeMillis();

        long iters = TEST_ITERS;
        try {
            iters = Long.parseLong(args[0]);
        } catch (Exception e) {
        }

        out("refSize=" + REF_SIZE + ", objSize=" + OBJ_SIZE + ", listSize=" + LIST_SIZE);

        for (long i = 0; i < iters; ++i) {
            if ((i & 0xFFFFF) == 0) {
                ctx.check();
            }

            switch (rnd.nextInt(100)) {
                case 0:
                    // switch context
                    // TODO
                    break;

                case 1:
                case 2:
                    // switch to list mode
                    ctx.alloc(LIST_SIZE);
                    list = new CollectibleList<>(ctx);

                default:
                    // allocation mode
                    ctx.alloc(OBJ_SIZE);
                    C c = new C(ctx, rnd.nextInt());
                    if (list != null) {
                        list.add(c);
                    }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        out("test completed in " + elapsed + "ms with " + xvm.refqueueTime + "ms spent reclaiming and " + xvm.gcTime + "ms spent in " + xvm.gcCount + " GCs; max tries=" + xvm.maxTries + "; reclaims(buddy=" + xvm.buddyReclaims + ", canary=" + xvm.canaryReclaims + ")");
    }

    public static class C extends Collectable {
        public C(Ctx ctx, int n) {
            super(ctx, OBJ_SIZE);
            this.n = n;
        }

        public final int n;
    }

    public static class CollectibleList<V> extends Collectable {
        final ArrayList<V> list;

        public CollectibleList(Ctx ctx) {
            super(ctx, LIST_SIZE);
            this.list = new ArrayList<>();
        }

        public boolean add(V v) {
            return list.add(v);
        }

        public int size() {
            return list.size();
        }
    }


    // let's assume:
    // - all code that allocates is running on an Ecstasy fiber
    // - that Ecstasy fiber is running on a Java virtual thread, which in turn is running (aka
    //   "mounted") on a Java carrier thread, which is an OS thread; we will run up to the number of
    //   hardware threads as virtual thread carriers
    // - we will have one ReferenceQueue per carrier thread; that carrier thread is responsible for
    //   draining that queue, and only that queue, e.g. at a safepoint check or at a safepoint
    // - each allocationwhat has a service and/or container id and a size, which is encoded into its
    //   weak reference (so that information can be used to account for the "free")
    // - no need to lock etc. if the data structures are only being written/read from that one same
    //   Java carrier thread
    //   - this enables us to maintain strong refs to the WeakReference implementations as a doubly
    //     linked list (easy deletion when the Referent goes away and the WeakReference gets
    //     reported via the ReferenceQueue)

    public static final class Xvm {
        public Xvm(long ram) {
            this.total = ram;
            this.available = ram;
        }

        // TODO this is ONLY for a single-threaded test; in the XVM runtime, each carrier thread has its
        //      own ReferenceQueue
        ReferenceQueue<?> refQueue = new ReferenceQueue<>();
        Reclaim<?> keepalive;

        int containerCount = 0;
        Container[] containers = new Container[8];
        public final long total;
        long available;         // bytes that can be allocated

        long refqueueTime;
        long gcTime;
        int gcCount;
        int drains;
        int maxTries;
        int canaryReclaims;
        int buddyReclaims;

        public Container createContainer() {
            if (containerCount >= containers.length) {
                containers = Arrays.copyOf(containers, containers.length * 2);
            }
            Container container = new Container(this, containerCount++);
            containers[container.id] = container;
            return container;
        }

        public Container getContainer(int cid) {
            return cid >= 0 && cid < containerCount ? containers[cid] : null;
        }

        public long alloc(long size) {
            // TODO this needs to be thread safe - available needs to be atomic
            assert size > 0;
            size = (max(size, 0x10000) + 0xFFFF) & ~0xFFFF;

            // TODO various rules and decisions: container limits, etc.

            if (size > available) {
                // this may indicate that we have run out of memory
                long startTime = currentTimeMillis();
                long pre = refqueueTime;
                // TODO kill container
                drainQueue();
                int tries = 0;
                while (size > available && tries++ < 10) {
//                    if (tries == 1) {
//                        out("uh-oh: available=" + available + ", size=" + size);
//                    }
                    if (tries > 10) {
                        out("try " + tries + " requesting GC: available=" + available + ", size=" + size);
                    }
                    System.gc();
                    Thread.yield();
                    ++gcCount;
                    drainQueue();
                }
                if (size > available) {
                    out("FAIL: available=" + available + ", size=" + size + ", list=" + (list == null ? "null" : list.size() + " elements"));
                    throw new RuntimeException();
                }
                if (tries > 0) {
//                    long elapsed = currentTimeMillis() - startTime;
//                    out("SUCCESS: required " + tries + "GC's in " + elapsed + "ms");
                    gcTime += currentTimeMillis() - startTime - (refqueueTime - pre);
                    if (tries > maxTries) {
                        maxTries = tries;
                    }
                }
            }

            available -= size;
            assert available >= 0;
            return size;
        }

        public void drainQueue() {
            ReferenceQueue queue = refQueue; // TODO

            long startMillis = currentTimeMillis();
            int count = 0;
            long bytes = 0;
            int prevCid = -1;
            Container container = null;
            long release = 0;
            boolean first = true;

            boolean restoreInterrupt = Thread.interrupted();
            try {
                while (queue.poll() instanceof CleanablePhantom ref) {
                    if (first) {
                        ++drains;
                    }

                    ref.clean(this);
                    if (ref instanceof Reclaim reclaim) {
                        int cid = reclaim.cid();
                        if (cid != prevCid) {
                            if (release > 0) {
                                bytes += release;
                                container.release(release);
                            }
                            prevCid = cid;
                            container = getContainer(cid);
                            release = 0;
                        }

                        release += reclaim.size();
                        ++count;
                        if (keepalive == reclaim) {
                            keepalive = reclaim.prev;
                        }

                        reclaim.unlink();   // this discards the strong ref to the weak ref
                    }
                }
            } finally {
                if (restoreInterrupt) {
                    Thread.currentThread().interrupt();
                }
            }

            if (count > 0) {
                if (release > 0) {
                    bytes += release;
                    container.release(release);
                }
                long before = available;
                available += bytes;

                int elapsed = (int) (currentTimeMillis() - startMillis);
//                out("reclaimed " + bytes + " bytes (" + before + "->" + available + ") from " + count + " objects in " + elapsed + "ms");
                refqueueTime += elapsed;
            }
        }
    }

    /**
     * This is a representation of a Container. All allocations occur on a fiber within a service
     * within a Container.
     */
    public static final class Container {
        public Container(Xvm xvm, int id) {
            this.xvm = xvm;
            this.id = id;
        }

        public final Xvm xvm;
        public final int id;
        long available;         // bytes allocated to this container but not yet handed out to a fiber
        long allocated;         // bytes allocated to fibers (contexts) from the xvm to this container

        public long alloc(long size) {
            assert size > 0;
            size = (max(size, 0x10000) + 0xFFFF) & ~0xFFFF;
            available -= size;
            if (available < 0) {
                // TODO size limit container?
                available += xvm.alloc(-available);
                assert available >= 0;
            }
            allocated += size;
            return size;
        }

        public void release(long bytes) {
            allocated -= bytes;
        }
    }

    /**
     * This represents the execution context for a fiber.
     */
    public static final class Ctx {
        public Ctx(Container container) {
            this.container = container;
            this.canary = new Canary();
            this.canaryReclaim = new Reclaim(canary, container.xvm.refQueue, container.id, 0);
        }

        public final Container container;

        long available;     // bytes committed from the container to this context
        long prevCheck;     // last check millis

        /**
         * the last "allocated" collectable
         */
        Collectable orphan;

        /**
         * the size of {@link #orphan}
         */
        long orphanSize;

        /**
         * the current canary
         */
        Canary canary;

        Reclaim<Canary> canaryReclaim;

        void alloc(long size) {
            if (RECLAIM) {
                available -= size;
                if (available < 0) {
                    // in theory, we could provide (1) the last time we asked (2) how much we were
                    // alloc'd last time etc.
                    available += container.alloc(-available);
                    assert available >= 0;
                }
            }
        }

        void check() {
            // if some amount of time has passed, we do a safepoint
            long now = currentTimeMillis();
            if (now != prevCheck) {
                prevCheck = now;
                container.xvm.drainQueue();
            }
        }


        /**
         * Called by {@link Collectable#Collectable}.
         *
         * @param newOrphan the new orphan (last allocated object)
         * @param size      the orphan's size
         */
        void watch(Collectable newOrphan, long size) {
            if (RECLAIM) {
                // finish adopting buddy; we started in next's constructor
                newOrphan.buddySize = orphanSize;
                newOrphan.buddyCanaryReclaim = canaryReclaim;

                if (canaryReclaim.size() >= CANARY_SIZE) {
                    container.xvm.keepalive = canaryReclaim.link(container.xvm.keepalive);
                    canary = new Canary();
                    canaryReclaim = new Reclaim(canary, container.xvm.refQueue, container.id, 0);
                }

                // record next as the new orphan
                orphan = newOrphan;
                orphanSize = size;
                canaryReclaim.adjustSize(size);
                newOrphan.canary = canary;
            }
        }
    }

    /**
     * Empty object used to detect the death of <em>all</em> that reference it.
     */
    public static final class Canary {
    }

    public abstract static class CleanablePhantom<V> extends PhantomReference<V> {
        public CleanablePhantom(V referent, ReferenceQueue<? super V> q) {
            super(referent, q);
        }

        /**
         * Called once the referent is unreachable
         */
        abstract void clean(Xvm xvm);
    }

    /**
     * Base class for objects which can be managed by the GC.
     */
    public static class Collectable extends CleanablePhantom<Collectable> {
        /**
         * The {@link Canary} we help keep alive, and whose death implies our death.
         */
        Canary canary;

        /**
         * The size of our buddy.
         */
        long buddySize;

        /**
         * Our buddies canary phantom
         */
        Reclaim<Canary> buddyCanaryReclaim;

        /**
         * Construct a {@link Collectable}.
         *
         * @param ctx  the {@link Ctx} for this object
         * @param size this instances size in bytes
         */
        public Collectable(Ctx ctx, long size) {
            super(ctx.orphan, ctx.orphan == null ? null : (ReferenceQueue) ctx.container.xvm.refQueue);
            ctx.watch(this, size);
        }

        public void clean(Xvm xvm) {
            ++xvm.buddyReclaims;
            long cbBuddy = this.buddySize;
            if (cbBuddy > 0) {
                this.buddySize = 0;
                xvm.getContainer(buddyCanaryReclaim.cid()).release(cbBuddy);
                xvm.available += cbBuddy;
                buddyCanaryReclaim.adjustSize(-cbBuddy);
                buddyCanaryReclaim = null; // dump garbage
            }
        }
    }

    /**
     * A {@link Reclaim} is used to account for memory reclaimed by the Java garbage collector (GC).
     * Each allocation of memory within Ecstasy also creates a Reclaim instance.
     *
     * @param <V> the referent type
     */
    public static final class Reclaim<V> extends CleanablePhantom<V> {
        // ----- constructors

        /**
         * Construct TODO
         *
         * @param v
         * @param q
         * @param cid
         * @param size
         */
        public Reclaim(V v, ReferenceQueue<V> q, int cid, long size) {
            super(v, q);
            assert (cid >= 0) & (cid <= 0xFFFFFF) & (size >= 0) & (size <= 0xFFFFFFFFFFL);
            info = (((long) cid) << 40) | size;
        }

        // ----- fields

        /**
         * Encodes allocation size and container id.
         */
        private long info;

        /**
         * A reference to the next Reclaim that was created by the current carrier (Java/OS) thread.
         * <p/>
         * Each Reclaim is part of a doubly linked list to hold a strong reference to each Reclaim
         * until after it has been processed.
         */
        private Reclaim next;

        /**
         * A reference to the previous Reclaim that was created by the current carrier (Java/OS)
         * thread.
         * <p/>
         * Each Reclaim is part of a doubly linked list to hold a strong reference to each Reclaim
         * until after it has been processed.
         */
        private Reclaim prev;

        // ----- methods

        /**
         * @return the id of the container that allocated the memory
         */
        public int cid() {
            return (int) (info >>> 40);
        }

        /**
         * @return the size of the allocated memory
         */
        public long size() {
            return info & 0xFFFFFFFFFFL;
        }

        /**
         * Adjust the size left to reclaim.
         *
         * @param adjustBytes the size to adjust by in bytes
         */
        public void adjustSize(long adjustBytes) {
            info += adjustBytes;
        }

        @Override
        void clean(Xvm xvm) {
            ++xvm.canaryReclaims;
        }

        /**
         * Unlink this Reclaim from the "keep-alive" linked list of Reclaim objects.
         */
        public void unlink() {
            if (prev != null) {
                prev.next = next;
            }
            if (next != null) {
                next.prev = prev;
            }

            // this isn't necessary after everything is working: no need to null out the next and
            // prev pointers of this Reclaim (since this Reclaim is no longer reachable)
            next = null;
            prev = null;
        }

        public Reclaim link(Reclaim that) {
            if (that != null) {
                this.next = that.next;
                that.next = this;
            }
            this.prev = that;
            return this;
        }
    }
}
