/**
 * A `BufferProvider` represents the stateful management and provision of [WriteBuffer] objects,
 * which in turn produce [ReadBuffer] objects.
 *
 * While it exposes only a simple API, the purpose of the provider is to enable dynamic data-driven
 * optimization for asynchronous I/O functionality, trading off between latency, throughput, and
 * memory utilization, by having a point (the stateful buffer provider itself) at which buffer
 * utilization can be measured and tracked.
 *
 * There are two fundamentally-different manners in which "buffers" are used:
 *
 * * Allocating a series of buffers in order to hold an entire structure, such as occurs when
 *   serializing an object graph, or rendering a large string. In this use case, no buffer can be
 *   released until some point after the entire data structure has been rendered into the buffer(s).
 *   Sometimes, the total buffer size cannot be predicted (or cannot be _efficiently_ predicted), so
 *   the total allocation may be extremely large. The ideal approach is to efficiently provide the
 *   necessary total amount of buffering, with both (i) a minimum number of buffer allocations, and
 *   (ii) a minimum amount of wasted (i.e. excess allocated) memory. The `BufferProvider` does
 *   **not** attempt to address this use case.
 *
 * * Allocating buffers that act as _temporary_ storage for _regions_ of a stream of data. When
 *   reading or writing sequentially, such as to/from a file or socket, using a certain number of
 *   buffers of a certain size will provide some combination or balance of (i) the highest
 *   throughput, (ii) the lowest latency, and (iii) a minimized memory footprint. While an optimal
 *   buffer _size_ tends to be fairly easy to predict, based on hardware configuration and fairly
 *   predictable OS behavior, the _number_ of buffers to use for an operation can be more
 *   challenging to determine. That certain number is not a fixed number, but can be dynamically
 *   determined by analyzing how the buffers are being used during the course of the I/O processing.
 *   This use case is the driving reason for the design of the `BufferProvider`.
 *
 * To visualize the purpose of the `BufferProvider`, consider the x-ray scanning machines at a
 * security screening checkpoint at a busy international airport. Each plastic bin that a traveler
 * places their belongings into represents one buffer. Buffers are automatically returned from the
 * unloading section at the end of the belt, to the loading section at the beginning of the belt.
 * Some number of travelers can simultaneously stand at the loading section of the belt, each able
 * to grab a new bucket, load it, and put it onto the belt, while at the other end, travelers are
 * emptying buckets and allowing the now-empty buckets to be re-used. While there are several flaws
 * in the analogy (e.g. buckets filled by a traveler are later unloaded by the same traveler), it
 * is the concept (maximizing throughput, maximizing concurrency, limiting latency, efficient reuse
 * of a limited number of buckets) that is compelling in this picture. The empty buckets being
 * filled are the [WriteBuffers](WriteBuffer), and the full buckets being emptied are the
 * [ReadBuffers](ReadBuffer).
 *
 * Now, imagine a traveling couple, one on the loading end and one on the unloading end, with a
 * ludicrous number of items to send through the x-ray, and a goal to use as few of buckets as
 * possible while getting all of their items through in the shortest amount of time. The key to the
 * optimization is to make sure that every time the person at the end of the belt finishes emptying
 * one bucket, that at that point in time, the next full one is present and ready to unload. In
 * order to minimize the number of buckets being used, it is necessary to pick a number of buckets
 * such that the process of filling and delivering the buckets has the highest possible likelihood
 * of always having another bucket ready to unload on the other end, but without wasting additional
 * buckets. If it is faster to load the buckets than unload them, it may be possible to do this with
 * as few as two buckets in total. If it is faster to unload the buckets, then in all likelihood the
 * person whose is unloading will often be waiting for the next bucket. But in some circumstances,
 * it may be possible to efficiently load a number of buckets at the same time (a bulk loading
 * process), and thus, one would like to determine what the optimal size of that bulk loading
 * process is in order to minimize the waiting on the other end, while still keeping the number of
 * buckets in use at any time to a minimum.
 *
 * For most actual I/O workloads, one end of the conveyor belt is the operating system. This may
 * complicates the picture, in that the OS generally has its own buffer management model, and it
 * may be at least semi-opaque to the application. Consider these six obvious I/O use cases:
 *
 * * Reading data from a socket is an inherently sequential process. When data arrives from the
 *   network, the physical interface (often generally referred to as a Network Interface Card, or
 *   NIC) writes the received data into a fixed RAM buffer (e.g. via DMA), which in turn is managed
 *   by the operating system. The buffer size is fixed, so when the buffer is filled up, TCP
 *   responds to any associated sender(s) with an `ACK` packet that specifies a window size of `0`,
 *   which indicates that all subsequent incoming data will be discarded until buffer space is
 *   available, which in turn causes the sender(s) to stop sending. Each physical interface may
 *   represent a number of IP addresses, each of which in turn can have any number of sockets open
 *   (each assigned a different port number on that address). In theory, a fixed size buffer may
 *   exist per physical interface, per IP address, and/or per socket. When an application is reading
 *   from a socket, it is actually reading from a buffer, which may be a copy of the previously
 *   described OS buffer, to allow the OS to quickly recycle its own buffers. In a true zero copy
 *   implementation, the application will read directly from the OS buffer, and failure to do so
 *   quickly enough has the potential to block incoming data. Outside of the zero-copy use case, it
 *   should be possible to avoid blocking the reader with only two buffers, by always filling one as
 *   soon as it has been fully read, while the reader is beginning to read from the other.
 *   Additional considerations are that (i) failure to read fast enough will allow the operating
 *   system's buffers to fill, which will then cause incoming network data to be rejected, and (ii)
 *   in most cases, the reader is able to extract its data from a buffer much faster than the
 *   network is able to deliver new data, so the expected stall point is on the network itself.
 *
 * * Writing data to a socket is an inherently sequential process. Generally, an area of memory (the
 *   "send buffer") is set aside by the operating system for data that will be reliably delivered
 *   over a socket. The data to be sent will be copied into this memory, where it will remain until
 *   the data is successfully transferred or the socket is closed. The NIC will copy from this
 *   memory when sending a packet, and that memory will be "held" until an ack is received for that
 *   packet's data. When an application "writes to a TCP/IP socket", the action of "writing to the
 *   socket" does not send any data over the network; rather, it simply stages the data in this
 *   memory area, ready to be sent -- as soon as possible -- by the operating system. When using a
 *   non-blocking API, the corresponding socket will only be selectable if there is room in its send
 *   buffer; when using a blocking socket API (such as the BSD `socket_write` function), any socket
 *   write will block until there is sufficient room in the socket's send buffer to stage the data
 *   being written. In both cases, the data to write is in the application memory space, perhaps
 *   already arranged in a buffer, and is then copied into a socket buffer. Since these APIs were
 *   designed to support C programs, a function like `socket_write` is designed to be called for
 *   each discrete piece of information to transmit (each `int`, `char[]`, `struct`, and so on),
 *   with the OS then guessing (using Nagle's algorithm) when to actually packetize the data and
 *   transmit it over the wire; in other words, a thousand `socket_write` calls might only produce
 *   a single packet, or a single socket_write call (with a large chunk of data) could produce a
 *   thousand packets.
 *
 * * Reading data sequentially from local flash storage, with low latency, high throughput, and high
 *   parallelism (via command queueing) across multiple readers and writers. The read latency _Lr_
 *   of the device is low, so if the processing latency _Lp_ is higher than _Lr_, then the low level
 *   I/O handler (i.e. the code filling the [WriteBuffer] objects from the disk) will need to have
 *   started filling the next `WriteBuffer` only when `Lr - Lp` time has passed in order to avoid a
 *   processing stall. It is likely that maximum throughput and minimum latency will be achieved
 *   with only two buffers for this use case, and the processing latency per buffer's worth of data
 *   will be the larger of `Lr` and `Lp`.
 *
 * * Writing data sequentially to local flash storage is similar in some ways to the "reading" use
 *   case. The difference is that the application may be able to fill the buffers far faster than
 *   even a fast flash drive can store that data. In this case, the provider may allow more than two
 *   buffers to be used, even though the time-to-last-byte-written will be the same, because it may
 *   allow the application to finish writing to the buffer and move on, long before the data
 *   finishes committing to disk.
 *
 * * Reading data sequentially from a local spinning magnetic drive, with high initial latency
 *   followed by decent sustained throughput. The high initial latency is caused by the positioning
 *   of the drive head, and subsequent sequential reads exhibit high sustained throughput until the
 *   OS serves another pending operation by repositioning the head. Because of the overhead of head
 *   positioning, the ideal buffer strategy is to use as many buffers as the provider allows, until
 *   the head is moved to handle another concurrent operation.
 *
 * * Writing data sequentially to a local spinning magnetic drive, with high initial latency
 *   followed by decent sustained throughput. As in the read case, the high initial latency is
 *   caused by the drive head being positioned, and the OS will often defer other I/O operations to
 *   the same drive as long as there are available write buffers to flush, (up to some reasonable
 *   time slice limit). The optimal allocation strategy is very similar to the read case, and the
 *   optimal application-unblocking behavior is similar to the flash storage write use case.
 *
 * The BufferProvider is responsible for maximizing performance (some combination of latency and
 * throughput) on the one hand, and minimizing memory footprint on the other hand. This is a complex
 * problem domain, because the actual I/O is expected to be async vis-a-vis the reader and writer.
 * That means, in the reading case, that an optimal buffer strategy will provide a [WriteBuffer] for
 * the I/O to fill such that the buffer can be filled and delivered over as a [ReadBuffer] at the
 * very instant that the reader reaches that region of the stream (i.e. is ready to read the first
 * byte of the newly delivered `ReadBuffer`). Ideally, the reader would never block on "reading the
 * next byte". In the writing case, the goal is either (i) to allow the writer to complete as
 * quickly as possible, regardless of the memory impact from buffering up to the entire contents to
 * be written, or more likely (ii) to block the writer if the actual I/O falls significantly behind
 * the writer, because the writer is filling the buffers faster than they can be processed by the
 * I/O service.
 *
 * Closing a buffer obtained from the `BufferProvider` will recycle the buffer, i.e. will return the
 * buffer to the `BufferProvider`, potentially to be pooled and reused.
 *
 * TODO
 * - how to track time to fill first buffer
 * - how to track time to fill each additional buffer
 */
interface BufferProvider
    {
    /**
     * Obtain the next buffer from the `BufferProvider`, if there is one available or if the
     * BufferProvider can allocate one.
     *
     * @return `True` iff the provider can provide a buffer
     * @return (conditional) a [WriteBuffer] that the caller can use
     */
    conditional WriteBuffer next();

    /**
     * Obtain one or more buffers from the `BufferProvider`. The number of bytes allocated may be
     * less or greater than the number of bytes requested; it is possible that the number of bytes
     * allocated is `0`, if the `BufferProvider` has been exhausted.
     *
     * @param bytesRequested  the number of bytes that the caller desires to place into one or more
     *                        [WriteBuffer] objects
     *
     * @return an array of one or more [WriteBuffer] objects
     * @return the number of bytes that can be held by the returned [WriteBuffer] objects
     */
    (WriteBuffer[] buffers, Int bytesAllocated) alloc(Int bytesRequested)
        {
        // this is a default implementation for a BufferProvider that does not
        WriteBuffer[] buffers        = new WriteBuffer[];
        Int           bytesAllocated = 0;

        do
            {
            if (WriteBuffer buffer := next())
                {
                assert buffer.offset == 0;
                buffers        += buffer;
                bytesAllocated += buffer.capacity;
                }
            }
        while (bytesAllocated < bytesRequested);

        return buffers, bytesAllocated;
        }

    /**
     * Determine if the `BufferProvider` has a known capacity limit.
     *
     * @return `True` iff the `BufferProvider` has a known capacity limit
     * @return (conditional) the maximum total number of buffer bytes that the `BufferProvider` is
     *         limited to having allocated at any given time
     */
    conditional Int capacityLimit();

    /**
     * Determine if the `BufferProvider` uses a single buffer size for all of its buffers.
     *
     * @return `True` iff the `BufferProvider` has a fixed buffer size
     * @return (conditional) the fixed buffer size
     */
    conditional Int fixedBufferSize();

    /**
     * The total number of buffers that the `BufferProvider` has allocated (and still exist).
     */
    @RO Int totalBuffers;

    /**
     * The total number of buffer bytes that the `BufferProvider` has allocated.
     */
    @RO Int totalBytes;

    /**
     * The number of buffers that the `BufferProvider` has provided that have not been returned to
     * the `BufferProvider`.
     */
    @RO Int consumedBuffers;

    /**
     * The number of buffer bytes that the `BufferProvider` has provided that have not been
     * returned to the `BufferProvider`.
     */
    @RO Int consumedBytes;

    /**
     * The number of buffers that the `BufferProvider` can provide at this point without actually
     * allocating any new buffers.
     */
    @RO Int availableBuffers.get()
        {
        return totalBuffers - consumedBuffers;
        }

    /**
     * The number of buffer bytes that the `BufferProvider` can still provide without actually
     * allocating any new buffers.
     */
    @RO Int availableBytes.get()
        {
        return totalBytes - consumedBytes;
        }
    }