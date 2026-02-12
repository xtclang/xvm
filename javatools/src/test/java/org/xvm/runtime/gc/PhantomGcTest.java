package org.xvm.runtime.gc;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.xvm.util.ShallowSizeOf;

import static java.lang.Math.max;
import static java.lang.System.currentTimeMillis;

class PhantomGcTest {
    public static void out(Object o) {
        System.out.println(o);
    }

    static final boolean RECLAIM = true;
    static final long RAM_SIZE = 100_000_000L;
    static final int TEST_ITERS = 100_000_000;

    static List<C> list = null;

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

        int iters = TEST_ITERS;
        try {
            iters = Integer.parseInt(args[0]);
        } catch (Exception e) {
        }

        final long refSize = ShallowSizeOf.instanceOf(Reclaim.class);
        final long objSize = refSize + ShallowSizeOf.instanceOf(C.class);
        final long listSize = refSize + ShallowSizeOf.instanceOf(ArrayList.class) + ShallowSizeOf.arrayOf(C.class, 16);
        out("refSize=" + refSize + ", objSize=" + objSize + ", listSize=" + listSize);

        for (int i = 0; i < iters; ++i) {
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
                    ctx.alloc(listSize);
                    ctx.watch(list = new ArrayList<C>(), listSize);


                default:
                    // allocation mode
                    ctx.alloc(objSize);
                    C c = new C(rnd.nextInt());
                    ctx.watch(c, objSize);
                    if (list != null) {
                        list.add(c);
                    }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        out("test completed in " + elapsed + "ms with " + xvm.refqueueTime + "ms spent reclaiming and " + xvm.gcTime + "ms spent in " + xvm.gcCount + " GCs; max tries=" + xvm.maxTries);
    }

    public static class C {
        public C(int n) {
            this.n = n;
        }

        public final int n;
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
        ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
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
                    if (tries == 1) {
                        out("uh-oh: available=" + available + ", size=" + size);
                    }
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
            ReferenceQueue<Object> queue = refQueue; // TODO

            long startMillis = currentTimeMillis();
            int count = 0;
            long bytes = 0;
            int prevCid = -1;
            Container container = null;
            long release = 0;
            boolean first = true;

            boolean restoreInterrupt = Thread.interrupted();
            try {
                while (queue.poll() instanceof Reclaim<?> reclaim) {
                    if (first) {
                        ++drains;
                    }

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
        }

        public final Container container;

        long available;     // bytes committed from the container to this context
        long prevCheck;     // last check millis

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

        void watch(Object o, long size) {
            if (RECLAIM) {
                container.xvm.keepalive = new Reclaim<>(list, container.xvm.refQueue, container.id, size).link(container.xvm.keepalive);
            }
        }
    }

    /**
     * A {@link Reclaim} is used to account for memory reclaimed by the Java garbage collector (GC).
     * Each allocation of memory within Ecstasy also creates a Reclaim instance.
     *
     * @param <V> the referent type
     */
    public static final class Reclaim<V> extends PhantomReference<V> {
        // ----- constructors

        /**
         * Construct TODO
         *
         * @param v
         * @param q
         * @param cid
         * @param size
         */
        public Reclaim(V v, ReferenceQueue<? super V> q, int cid, long size) {
            super(v, q);
            assert (cid >= 0) & (cid <= 0xFFFFFF) & (size >= 0) & (size <= 0xFFFFFFFFFFL);
            info = (((long) cid) << 40) | size;
        }

        // ----- fields

        /**
         * Encodes allocation size and container id.
         */
        private final long info;

        /**
         * A reference to the next Reclaim that was created by the current carrier (Java/OS) thread.
         * <p/>
         * Each Reclaim is part of a doubly linked list to hold a strong reference to each Reclaim
         * until after it has been processed.
         */
        private Reclaim<?> next;

        /**
         * A reference to the previous Reclaim that was created by the current carrier (Java/OS)
         * thread.
         * <p/>
         * Each Reclaim is part of a doubly linked list to hold a strong reference to each Reclaim
         * until after it has been processed.
         */
        private Reclaim<?> prev;

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

        public Reclaim<?> link(Reclaim<?> that) {
            if (that != null) {
                this.next = that.next;
                that.next = this;
            }
            this.prev = that;
            return this;
        }
    }
}
