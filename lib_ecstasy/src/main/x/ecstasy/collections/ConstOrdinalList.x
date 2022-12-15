import ecstasy.io.DataOutput;
import ecstasy.io.PackedDataOutput;
import ecstasy.io.ByteArrayOutputStream;

/**
 * The ConstOrdinalList is an immutable List implementation based on a compressed byte array value.
 */
const ConstOrdinalList
        implements List<Int>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ConstOrdinalList from an array of integer values.
     *
     * @param contents  the compressed form of a bit-set
     */
    construct(Byte[] contents)
        {
        this.contents = contents;

        (size, firstOffset) = contents.unpackInt(0);
        if (size > 0)
            {
            (defaultVal, firstOffset) = contents.unpackInt(firstOffset);
            (bitsPerVal, firstOffset) = contents.unpackInt(firstOffset);
            (firstIndex, firstOffset) = contents.unpackInt(firstOffset);
            }
        }

    /**
     * Construct a ConstOrdinalList from an array of integer values.
     *
     * @param values  an array of integer values
     */
    construct(Int[] values, Int fastAccess = 0)
        {
        assert:arg 0 <= fastAccess <= values.size;

        @PackedDataOutput ByteArrayOutputStream out = new @PackedDataOutput ByteArrayOutputStream();

        Int valueCount = values.size;
        out.writeInt64(valueCount);

        if (valueCount > 0)
            {
            (Int highest, Int defaultVal) = analyzeValues(values);
            out.writeInt64(defaultVal);

            // calculate bits per value
            Int bitsPerVal = highest.leftmostBit.trailingZeroCount + 1 & 0x3F;
            assert 0 < bitsPerVal <= 63;
            out.writeInt64(bitsPerVal);

            // calculate a minimum run length based on expected node sizes (assume 6 byte overhead)
            Int minRunLen = (6 * 8 - 1) / bitsPerVal + 1;

            // split the array of integers up into nodes
            Node[] nodes = buildNodeList(values, minRunLen, defaultVal);

            // make sure that the desired size "fast" node exists to cover the first chunk of the
            // array
            ensureFastNode(nodes, values, fastAccess, minRunLen);

            // last field in the header is the index of the first value from the first node
            out.writeInt64(nodes.size == 0 ? valueCount : nodes[0].index);

            if (nodes.size > 0)
                {
                // link the nodes' "jump" pointers
                createSkips(nodes, 0 ..< nodes.size);

                // turn each node into a byte array, and write out the nodes
                Byte[][] nodesBytes = toBytes(nodes, values, bitsPerVal);
                for (Byte[] nodeBytes : nodesBytes)
                    {
                    out.writeBytes(nodeBytes);
                    }
                }
            }

        construct ConstOrdinalList(out.bytes);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The `Byte[]` that holds the compressed constant data for this list.
     */
    Byte[] contents;

    @Override
    Int size;

    /**
     * The default value for any missing ranges.
     */
    Int defaultVal;

    /**
     * The number of bits required to hold any compressed values.
     */
    Int bitsPerVal;

    /**
     * The index of the first node in the compressed list.
     */
    Int firstIndex;

    /**
     * The offset of the first node in the [contents] array.
     */
    Int firstOffset;


    // ----- List methods --------------------------------------------------------------------------

    @Override
    @Op("[]") Int getElement(Int index)
        {
        assert:bounds 0 <= index < size;

        Int indexCurrent = firstIndex;
        if (index < indexCurrent)
            {
            return defaultVal;
            }

        Int offsetCurrent = firstOffset;
        while (True)
            {
            (Int indexSkip, offsetCurrent) = contents.unpackInt(offsetCurrent);
            if (indexSkip != 0)
                {
                (Int offsetSkip, offsetCurrent) = contents.unpackInt(offsetCurrent);
                if (index >= indexCurrent + indexSkip)
                    {
                    indexCurrent  += indexSkip;
                    offsetCurrent += offsetSkip;
                    continue;
                    }
                }

            // three possibilities for the value's position at this point:
            // 1) between the current point and the end of values present in this node
            // 2) after this node but before the next node (or there is no next node)
            // 3) somewhere after the start of the next node (i.e. only if there is a next node)
            (Int indexNext, offsetCurrent) = contents.unpackInt(offsetCurrent);
            (Int nodeLen  , offsetCurrent) = contents.unpackInt(offsetCurrent);
            if (nodeLen < 0)
                {
                (Int nodeVal, offsetCurrent) = contents.unpackInt(offsetCurrent);
                if (index < indexCurrent - nodeLen)
                    {
                    return nodeVal;
                    }
                }
            else
                {
                if (index < indexCurrent + nodeLen)
                    {
                    return unpackOne(contents, offsetCurrent, index - indexCurrent, bitsPerVal);
                    }

                offsetCurrent += (nodeLen * bitsPerVal + 7) / 8;
                }

            if (indexNext != 0 && index >= indexCurrent + indexNext)
                {
                indexCurrent += indexNext;
                }
            else
                {
                return defaultVal;
                }
            }
        }

    @Override
    Iterator<Int> iterator()
        {
        return new Iterator<Int>()
            {
            Int     curIndex       = 0;

            Int     curNodeIndex   = 0;
            Int     curNodeSize    = 0;

            Boolean runLenEnc      = False;
            Int     repeatingVal   = 0;         // run length node only
            Int     packedOffset   = 0;         // packed node only

            Int     nextNodeIndex  = 0;
            Int     nextNodeOffset = firstOffset;

            @Override
            conditional Int next()
                {
                // check if the current node is exhausted
                if (curIndex >= nextNodeIndex && !loadNextNode())
                    {
                    // the iterator is exhausted
                    return False;
                    }

                // check if the value is between the current node and the next node
                Int val;
                if (curIndex >= curNodeIndex + curNodeSize)
                    {
                    val = defaultVal;
                    }
                else if (runLenEnc)
                    {
                    val = repeatingVal;
                    }
                else
                    {
                    // packed array mode
                    val = unpackOne(contents, packedOffset, curIndex - curNodeIndex, bitsPerVal);
                    }

                ++curIndex;
                return True, val;
                }

            private Boolean loadNextNode()
                {
                // check if the iterator is exhausted
                if (curIndex >= this.ConstOrdinalList.size)
                    {
                    return False;
                    }

                if (nextNodeIndex == 0 && firstIndex > 0)
                    {
                    // the compressed list has a "hole" at the beginning; pretend that it is
                    // represented as an RLE node
                    runLenEnc     = True;
                    repeatingVal  = defaultVal;
                    curNodeSize   = firstIndex;
                    nextNodeIndex = firstIndex;
                    }
                else
                    {
                    // advance to the next node
                    curNodeIndex = nextNodeIndex;

                    // skip the jump-forward info
                    (Int jumpIndex, nextNodeOffset) = contents.unpackInt(nextNodeOffset);
                    if (jumpIndex != 0)
                        {
                        (_, nextNodeOffset) = contents.unpackInt(nextNodeOffset);
                        }

                    // read the relative index of the next node
                    (nextNodeIndex, nextNodeOffset) = contents.unpackInt(nextNodeOffset);
                    nextNodeIndex += curNodeIndex; // adjust from relative to absolute

                    // read the number of values in the current node
                    (curNodeSize, nextNodeOffset) = contents.unpackInt(nextNodeOffset);
                    if (curNodeSize < 0)
                        {
                        runLenEnc   = True;
                        curNodeSize = -curNodeSize;
                        (repeatingVal, nextNodeOffset) = contents.unpackInt(nextNodeOffset);
                        }
                    else
                        {
                        runLenEnc       = False;
                        packedOffset    = nextNodeOffset;
                        nextNodeOffset += (curNodeSize * bitsPerVal + 7) / 8;
                        }
                    }

                return True;
                }
            };
        }


    // ----- packed array --------------------------------------------------------------------------

    /**
     * Given an array of integer values, `vals`, pack a portion of that array beginning with the
     * value at the `packFirst` index, packing a total of `packCount` values using `bitsPerVal` bits
     * for each value.
     *
     * @param vals        an array containing the values to pack
     * @param packFirst   the index of the first value to pack
     * @param packCount   the number of values to pack
     * @param bitsPerVal  the number of bits to use for each value
     *
     * @return a byte array containing the packed integer values
     */
    static Byte[] pack(Int[] vals, Int packFirst, Int packCount, Int bitsPerVal)
        {
        Int    binLen = (packCount * bitsPerVal + 7) / 8;
        Byte[] binVal = new Byte[binLen];

        for (Int i = 0; i < packCount; ++i)
            {
            Int val       = vals[packFirst+i];
            Int bitOffset = i * bitsPerVal;
            while (val != 0)
                {
                Int  byteOffset = bitOffset / 8;
                Byte byte       = binVal[byteOffset];

                // how many bits will fit into this Byte?
                Int bitCount = 8 - (bitOffset & 0x7);
                Int bitShift = 8 - bitCount;

                byte |= (val << bitShift).toByte(); // TODO CP REVIEW GG this should throw .. we need a "slice byte"
                binVal[byteOffset] = byte;

                val     >>>= bitCount;
                bitOffset += bitCount;
                }
            }

        return binVal;
        }

    /**
     * Given a sequence of `packedCount` integers, each of `bitsPerVal` bits in size, packed
     * end-to-end inside the `contents` byte array starting at the offset `packedOffset`, extract
     * and unpack all of those values.
     *
     * @param contents      an array of bytes
     * @param packedOffset  an offset into the array of bytes where the packed integers are located
     * @param packedCount   the number of packed integers to unpack
     * @param bitsPerVal    the size of each packed integer
     *
     * @return an array of the unpacked integer values
     */
    static Int[] unpack(Byte[] contents, Int packedOffset, Int packedCount, Int bitsPerVal)
        {
        return new Int[packedCount](i -> unpackOne(contents, packedOffset, i, bitsPerVal));
        }

    /**
     * Given a sequence of integers, each of `bitsPerVal` bits in size, packed end-to-end inside
     * the `contents` byte array starting at the offset `packedOffset`, extract the `n`-th value
     * where `n` is specified by `packedIndex`.
     *
     * @param contents      an array of bytes
     * @param packedOffset  an offset into the array of bytes where the packed integers are located
     * @param packedIndex   the index of the packed integer to unpack
     * @param bitsPerVal    the size of each packed integer
     *
     * @return the unpacked integer value
     */
    static Int unpackOne(Byte[] contents, Int packedOffset, Int packedIndex, Int bitsPerVal)
        {
        Int val = 0;

        Int bitOffset     = packedIndex * bitsPerVal;
        Int remainingBits = bitsPerVal;
        while (remainingBits > 0)
            {
            Int  byteVal    = contents[packedOffset + bitOffset / 8];
            Int  partOffset = bitOffset & 0x7;
            Int  partLength = (8 - partOffset).minOf(remainingBits);

            val |= byteVal >>> partOffset & (1 << partLength) - 1 << bitsPerVal - remainingBits;

            bitOffset      += partLength;
            remainingBits  -= partLength;
            }

        return val;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Given an array of values, find the highest value and select the most common value as a
     * default value to use.
     */
    private static (Int highest, Int defaultVal) analyzeValues(Int[] values)
        {
        Map<Int, Int> map = new HashMap();
        Int highest = -1;
        Int mostest =  0;       // parents mistakenly tell their kids that this is not a word
        for (Int n : values)
            {
            // verify no negative values
            assert:arg n >= 0;

            // count incidence of values (outside of runs)
            map.process(n, e -> {e.value = e.exists ? e.value + 1 : 1;});

            // determine highest magnitude value
            if (n > highest)
                {
                highest = n;
                }
            }

        // determine which value can be omitted (i.e. pick a default value)
        Int defaultVal = -1;
        for ((Int n, Int count) : map)
            {
            if (count > mostest)
                {
                defaultVal = n;
                mostest    = count;
                }
            }

        return highest, defaultVal;
        }

    /**
     * A scratch-pad structure used to collect information about each node that will make up the
     * compressed list.
     */
    private static class Node
        {
        Int     index;
        Int     length;
        Boolean runLenEnc;      // indicates either an RLE node or a packed-array node
        Int     jumpIndex;
        }

    /**
     * Chop up the array of values into a sequence of run-length-encoded and packed-array nodes.
     */
    private static Node[] buildNodeList(Int[] values, Int minRunLen, Int defaultVal)
        {
        // identify the runs within the array, and build a sequence of nodes (run length encoded
        // nodes, and packed-array nodes)
        Int    firstNumIndex = -1;  // index of first "not picked up into a node" non-default number
        Int    lastNumIndex  = -1;  // index of last "not picked up into a node" non-default number
        Int    prevVal       = -1;
        Int    runLen        = 0;
        Node[] nodes         = new Node[];
        Loop: for (Int val : values)
            {
            if (val == prevVal)
                {
                ++runLen;
                }
            else
                {
                if (runLen >= minRunLen)
                    {
                    createNodes(nodes, values, Loop.count, prevVal, runLen, minRunLen,
                                firstNumIndex, lastNumIndex, defaultVal);
                    firstNumIndex = -1;
                    lastNumIndex  = -1;
                    }
                runLen = 1;
                }

            if (val != defaultVal)
                {
                if (firstNumIndex < 0)
                    {
                    firstNumIndex = Loop.count;
                    }
                lastNumIndex = Loop.count;
                }

            prevVal = val;
            }

        createNodes(nodes, values, values.size, prevVal, runLen, minRunLen,
                    firstNumIndex, lastNumIndex, defaultVal);

        return nodes;
        }

    /**
     * Create zero, one, or two nodes (an optional run-length-encoded node followed by an optional
     * packed-array node) based on an analysis of a portion of the array of values.
     */
    private static void createNodes(Node[] nodes, Int[] values, Int index,
                                    Int runVal, Int runLen, Int minRunLen,
                                    Int firstNumIndex, Int lastNumIndex, Int defaultVal)
        {
        if (runLen >= minRunLen)
            {
            Int runIndex = index - runLen;

            // the last `runLen` values form a run, so we will create an RLE node for it, but
            // first we need to create an array node in front of it if there were any values
            // that appeared before the run that we did not previously capture in a node
            if (0 <= firstNumIndex < runIndex)
                {
                // only take numbers up to the beginning of the run (and don't keep any trailing
                // default values)
                if (lastNumIndex >= runIndex)
                    {
                    lastNumIndex = runIndex - 1;
                    while (lastNumIndex > firstNumIndex && values[lastNumIndex] == defaultVal)
                        {
                        --lastNumIndex;
                        }
                    }

                Node node      = new Node();
                node.runLenEnc = False;
                node.index     = firstNumIndex;
                node.length    = lastNumIndex - firstNumIndex + 1;
                nodes.add(node);
                }

            if (values[runIndex] != defaultVal)
                {
                // create the RLE node
                Node node      = new Node();
                node.runLenEnc = True;
                node.index     = runIndex;
                node.length    = runLen;
                nodes.add(node);
                }
            }
        else if (firstNumIndex >= 0)
            {
            // create a packed-array node
            Node node      = new Node();
            node.runLenEnc = False;
            node.index     = firstNumIndex;
            node.length    = lastNumIndex - firstNumIndex + 1;
            nodes.add(node);
            }
        }

    /**
     * Collapse as many nodes as necessary at the beginning of the list into a single node to ensure
     * O(1) access to that portion of the information.
     */
    private static void ensureFastNode(Node[] nodes, Int[] values, Int fastAccess, Int minRunLen)
        {
        // make sure that the initial block covers the entire "fast" range as a single block
        if (fastAccess > 0)
            {
            // figure out how many nodes have to be combined to create the "fast" node
            Int span = 0;
            for (Node node : nodes)
                {
                if (node.index >= fastAccess)
                    {
                    break;
                    }

                ++span;
                }

            if (span <= 1)
                {
                return;
                }

            // if the last node to combine to make the fast node is an RLE node, then see if the RLE
            // node represents a run that is long enough to be split into a portion that goes into
            // the "fast" node, and a portion that stays as an RLE node
            Node nodeLast   = nodes[span - 1];
            Int  firstIndex = nodes[0].index;
            Int  lastIndex  = nodeLast.index + nodeLast.length - 1;
            if (nodeLast.runLenEnc && lastIndex - fastAccess > minRunLen)
                {
                lastIndex = fastAccess - 1;
                Int cAdjust = fastAccess - nodeLast.index;
                assert cAdjust > 0;
                nodeLast.index  += cAdjust;
                nodeLast.length -= cAdjust;
                --span;
                }

            // drop the extra nodes that we're collapsing into the fast node
            if (span > 1)
                {
                nodes.deleteAll(1 ..< span);
                }

            // build the fast node and use it in place of the first node
            Node nodeFast      = new Node();
            nodeFast.index     = firstIndex;
            nodeFast.length    = lastIndex - firstIndex + 1;
            nodeFast.runLenEnc = False;
            nodes[0]           = nodeFast;
            }
        }

    /**
     * Link the nodes' "jump" pointers.
     */
    private static void createSkips(Node[] nodes, Range<Int> indexes)
        {
        Int count = indexes.size;
        if (count <= 2)
            {
            return;
            }

        Int firstIndex = indexes.effectiveLowerBound;
        Int lastIndex  = indexes.effectiveLowerBound;

        Node node = nodes[firstIndex];
        assert node.jumpIndex == 0;

        Int relativeJump = (count-1).leftmostBit;
        node.jumpIndex = nodes[firstIndex + relativeJump].index;

        createSkips(nodes, firstIndex+1 ..< firstIndex+relativeJump);
        createSkips(nodes, firstIndex+relativeJump .. lastIndex);
        }

    /**
     * Serialize the passed array of nodes.
     */
    private static Byte[][] toBytes(Node[] nodes, Int[] values, Int bitsPerVal)
        {
        // turn the nodes into bytes
        Byte[][] nodesBytes = new Byte[][];

        Node? nodeNext = Null;
        for (Int i : nodes.size >.. 0)
            {
            Node node = nodes[i];
            assert nodeNext?.index > node.index;

            Int    jumpIndex = node.jumpIndex == 0 ? 0 : node.jumpIndex - node.index;
            Int    valCount  = node.runLenEnc ? -node.length : node.length;
            Int    nextIndex = nodeNext?.index - node.index : valCount;
            Int    val       = 0;
            Byte[] packed    = [];
            if (node.runLenEnc)
                {
                val = values[node.index];
                }
            else
                {
                packed = pack(values, node.index, node.length, bitsPerVal);
                }

            Int jumpOffset = 0;
            if (jumpIndex != 0)
                {
                // skip over the rest of this node
                jumpOffset += DataOutput.packedIntLength(nextIndex);
                jumpOffset += DataOutput.packedIntLength(valCount);
                jumpOffset += node.runLenEnc
                        ? DataOutput.packedIntLength(val)
                        : packed.size;

                // then we have to skip over any nodes in between the "from" and "to" nodes
                Int skip = i + 1;
                Loop: while (True)
                    {
                    Node nodeSkip = nodes[skip];
                    switch (nodeSkip.index <=> node.jumpIndex)
                        {
                        case Lesser:
                            jumpOffset += nodesBytes[skip++].size;
                            break;

                        case Equal:
                            break Loop;

                        case Greater:
                            assert;
                        }
                    }
                }

            @PackedDataOutput ByteArrayOutputStream out = new @PackedDataOutput ByteArrayOutputStream();
            out.writeInt64(jumpIndex);
            if (jumpIndex != 0)
                {
                out.writeInt64(jumpOffset);
                }
            out.writeInt64(nextIndex);
            out.writeInt64(valCount);
            if (node.runLenEnc)
                {
                out.writeInt64(val);
                }
            else
                {
                out.writeBytes(packed);
                }
            nodesBytes[i] = out.bytes;

            nodeNext = node;
            }

        return nodesBytes;
        }
    }