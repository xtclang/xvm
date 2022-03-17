package org.xvm.util;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.PackedInteger.packedLength;
import static org.xvm.util.PackedInteger.unpackInt;
import static org.xvm.util.PackedInteger.writeLong;


/**
 * An immutable List of Integer implementation that stores a compressed form of an {@code int[]} or
 * a {@code List<Integer>} in a byte array.
 * <p/>
 * This is not intended to be a general purpose compressor; it is designed for a small set of use
 * cases, such as compressing a list of enumeration ordinals.
 * <p/>
 * See the notes on {@link ConstBitSet} for some background on the high-level design goals for the
 * data structure.
 * <p/>
 * Several forms of compression are used:
 * </li><li>Repetition of the same value can be represented as a "repeating node";
 * </li><li>The most common value can be omitted altogether, since absence of data implies that
 *          value;
 * </li><li>When values need to be stored, only the necessary significant number of bits need be
 *          used.
 * </li></ul>
 * <p/>
 * The format is a sequence of variable-length nodes, of several different forms. Each form of node
 * is composed of:
 * <ul><li>An <i>implicit</i> byte id for the node (a byte id is the index into a <i>conceptual</i>
 *         byte array that backs the bit set);
 * </li><li>A <i>skip</i> byte id (encoded as a relative value to this node's implicit byte id) for
 *          the node that can be skipped forward to in the bit set, or zero to indicate that no skip
 *          forward option is available from this node;
 * </li><li>The number of bytes to skip forward (this field only exists if the skip byte id is
 *          non-zero);
 * </li><li>A <i>next</i> byte id (encoded as a relative value to this node's implicit byte id) for
 *          the node that immediately follows the current node, or zero to indicate that this is the
 *          last node in the bit set (i.e. EOF).
 * </li></ul>
 * <p/>
 * The run-length node form adds:
 * </li><li>The run length (encoded as a negative value to indicate RLE);
 * </li><li>The run value.
 * </li></ul>
 * <p/>
 * The array node form adds:
 * </li><li>The number of values;
 * </li><li>The bytes necessary to hold those values (typically fewer bytes than the number of
 *          values, since only the minimum necessary LSBs for each value are stored).
 * </li></ul>
 * The header is composed of compressed integers:
 * </li><li>The array length;
 * </li><li>The default element value;
 * </li><li>The number of bits per element (for non-RLE nodes);
 * </li><li>The id of the first node.
 * </li></ul>
 * <p/>
 * The array node form adds:
 * </li><li>The number of values;
 * </li><li>The bytes necessary to hold those values (typically fewer bytes than the number of
 *          values, since only the minimum necessary LSBs for each value are stored).
 * </li></ul>
 * <p/>
 * Considered and rejected: The values in the list may be representable with smaller values by
 * replacing frequently encountered large values with smaller identity values that represent the
 * original values, with the array of original values indexed by the identity values (and likely
 * stored at the end of the data structure). This capability was omitted because it is compositional
 * with this data structure, so could be implemented separately.
 */
public class ConstOrdinalList
        extends AbstractList<Integer>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new ConstIntList from a List of Integer values.
     *
     * @param list  the List of Integer values to compress
     */
    public ConstOrdinalList(List<Integer> list)
        {
        this(list.stream().mapToInt(n -> n).toArray());
        }

    /**
     * Construct a new ConstIntList from an array of int values.
     *
     * @param an  the compressed form of a bit-set
     */
    public ConstOrdinalList(int[] an)
        {
        assert an != null;
        m_ab = compress(an, 0);
        }

    /**
     * Construct a new ConstIntList from the already-compressed form.
     *
     * @param ab  the compressed form of a bit-set
     */
    public ConstOrdinalList(byte[] ab)
        {
        assert ab != null;
        m_ab = ab;
        }


    // ----- accessors --------------------------------------------------------------------------

    /**
     * @return a Java int array containing a copy of the same data
     */
    public int[] toIntArray()
        {
        return decompress(m_ab);
        }

    /**
     * @return a copy of this ConstIntList's compressed data in its binary form
     */
    public byte[] getBytes()
        {
        return m_ab.clone();
        }


    // ----- List API ------------------------------------------------------------------------------

    @Override
    public int size()
        {
        long lCount = unpackInt(m_ab, 0);
        return (int) lCount;
        }

    @Override
    public Integer get(int index)
        {
        if (index < 0)
            {
            throw new IndexOutOfBoundsException("negative index: " + index);
            }

        long lIntVal = unpackInt(m_ab, 0);
        int  ofCur   = (int) (lIntVal >>> 32);
        int  nCount  = (int) lIntVal;
        if (index >= nCount)
            {
            throw new IndexOutOfBoundsException("requested index=" + index
                    + ", highest legal index=" + (nCount-1));
            }

        lIntVal      = unpackInt(m_ab, ofCur);
        ofCur       += (int) (lIntVal >>> 32);
        int nDefault = (int) lIntVal;

        lIntVal      = unpackInt(m_ab, ofCur);
        ofCur       += (int) (lIntVal >>> 32);
        int cBitsPer = (int) lIntVal;

        lIntVal      = unpackInt(m_ab, ofCur);
        ofCur       += (int) (lIntVal >>> 32);
        int idCur    = (int) lIntVal;

        if (index < idCur)
            {
            return nDefault;
            }

        while (true)
            {
            lIntVal    = unpackInt(m_ab, ofCur);
            ofCur     += (int) (lIntVal >>> 32);
            int idSkip = (int) lIntVal;

            if (idSkip != 0)
                {
                lIntVal    = unpackInt(m_ab, ofCur);
                ofCur     += (int) (lIntVal >>> 32);
                int ofSkip = (int) lIntVal;

                if (index >= idCur + idSkip)
                    {
                    idCur += idSkip;
                    ofCur += ofSkip;
                    continue;
                    }
                }

            // three possibilities for the value's position at this point:
            // 1) between the current point and the end of values present in this node
            // 2) after this node but before the next node (or there is no next node)
            // 3) somewhere after the start of the next node (i.e. only if there is a next node)
            lIntVal    = unpackInt(m_ab, ofCur);
            ofCur     += (int) (lIntVal >>> 32);
            int idNext = (int) lIntVal;

            lIntVal    = unpackInt(m_ab, ofCur);
            ofCur     += (int) (lIntVal >>> 32);
            int nLen   = (int) lIntVal;

            if (nLen < 0)
                {
                lIntVal    = unpackInt(m_ab, ofCur);
                ofCur     += (int) (lIntVal >>> 32);
                int nVal   = (int) lIntVal;

                if (index < idCur - nLen)
                    {
                    return nVal;
                    }
                }
            else
                {
                if (index < idCur + nLen)
                    {
                    return unpackOne(m_ab, ofCur, cBitsPer, index - idCur);
                    }

                ofCur += (nLen * cBitsPer + 7) / 8;
                }

            if (idNext != 0 && index >= idCur + idNext)
                {
                idCur += idNext;
                }
            else
                {
                return nDefault;
                }
            }
        }

    @Override
    public Iterator<Integer> iterator()
        {
        long lIntVal = unpackInt(m_ab, 0);
        int  ofCur   = (int) (lIntVal >>> 32);
        int  nCount  = (int) lIntVal;
        if (nCount == 0)
            {
            return Collections.emptyIterator();
            }

        lIntVal      = unpackInt(m_ab, ofCur);
        ofCur       += (int) (lIntVal >>> 32);
        int nDefault = (int) lIntVal;

        lIntVal      = unpackInt(m_ab, ofCur);
        ofCur       += (int) (lIntVal >>> 32);
        int cBitsPer = (int) lIntVal;

        lIntVal      = unpackInt(m_ab, ofCur);
        ofCur       += (int) (lIntVal >>> 32);
        int idFirst  = (int) lIntVal;
        int ofFirst  = ofCur;

        return new Iterator<Integer>()
            {
            final byte[] ab      = m_ab;
            int          idNode  = 0;
            RawNode      nodeCur = null;
            int          ofNext  = ofFirst;
            int          iNext   = 0;               // counter *within* the current node

            @Override
            public boolean hasNext()
                {
                return currentNode() != null;
                }

            @Override
            public Integer next()
                {
                RawNode node = currentNode();
                if (node == null)
                    {
                    throw new NoSuchElementException();
                    }

                // RLE mode
                if (node.cVals < 0)
                    {
                    return (iNext++ >= -node.cVals)
                            ? nDefault
                            : node.nVal;
                    }

                // array mode
                if (iNext < node.cVals)
                    {
                    return unpackOne(node.abVals, node.ofVals, cBitsPer, iNext++);
                    }

                ++iNext;
                return nDefault;
                }

            RawNode currentNode()
                {
                RawNode node = nodeCur;
                if (node == null)
                    {
                    nodeCur = node = new RawNode();
                    if (idFirst > 0)
                        {
                        // configure the node to be an RLE of the default value
                        node.cVals  = -idFirst;
                        node.nVal   = nDefault;
                        node.idNext = idFirst;
                        return node;
                        }
                    }

                if (iNext >= node.idNext)
                    {
                    if (ofNext >= ab.length)
                        {
                        return null;
                        }

                    idNode += node.idNext;
                    ofNext += fromBytes(node, ab, ofNext, cBitsPer);
                    iNext   = 0;

                    if (node.idNext == 0)
                        {
                        // extend the last node to the end of the array by pretending that it has
                        // a next node that follows it
                        node.idNext = nCount - idNode;
                        }
                    }

                return node;
                }
            };
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Compress an int array into the ConstIntList compressed format.
     *
     * @param an     an array of integers to compress
     * @param cFast  the number of initial integers in the array to provide O(1) access to
     *
     * @return the ConstIntList's compressed data in its binary form
     */
    public static byte[] compress(int[] an, int cFast)
        {
        assert an != null;
        assert cFast >= 0 && cFast <= an.length;

        int cVals = an.length;
        if (cVals == 0)
            {
            try
                {
                ByteArrayOutputStream outRaw = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(outRaw);

                writeLong(out, cVals);
                return outRaw.toByteArray();
                }
            catch (IOException e)
                {
                throw new RuntimeException(e);
                }
            }

        Map<Integer, Integer> mapN = new HashMap<>();
        int nHigh = -1;
        for (int i = 0; i < cVals; ++i)
            {
            // verify all values >0
            int n = an[i];
            assert n >= 0;

            // count incidence of values (outside of runs)
            mapN.compute(n, (k, v) -> (v==null?0:v) + 1);

            // determine highest magnitude value
            if (n > nHigh)
                {
                nHigh = n;
                }
            }

        // determine which value can be omitted (i.e. pick an implicit value)
        int nDefault = -1;
        int cDefault = 0;
        for (Entry<Integer, Integer> entry : mapN.entrySet())
            {
            if (entry.getValue() > cDefault)
                {
                nDefault = entry.getKey();
                cDefault = entry.getValue();
                }
            }

        // calculate bits per value
        int cBitsPer = (Integer.numberOfTrailingZeros(Integer.highestOneBit(nHigh)) + 1) & 0x1F;
        assert cBitsPer > 0 && cBitsPer <= 32;

        // calculate a minimum run length based on expected node sizes (assume 6 byte overhead)
        int cMinRun = 47 / cBitsPer + 1;

        // split the array of integers up into nodes
        List<Node> list = buildNodeList(an, cMinRun, nDefault);

        // make sure that the desired size "fast" node exists to cover the first chunk of the array
        ensureFastNode(list, cFast, cMinRun, an);

        // link "next node" pointers
        Node[] aNode  = list.toArray(new Node[0]);
        int    cNodes = aNode.length;
        for (int i = 1; i < cNodes; ++i)
            {
            aNode[i-1].next = aNode[i];
            }

        // link "skip node" pointers
        createSkips(aNode, 0, aNode.length-1);

        // turn each node into a byte array
        byte[][] aabNode = toBytes(aNode, cBitsPer);

        try
            {
            ByteArrayOutputStream outRaw = new ByteArrayOutputStream();
            DataOutputStream      out    = new DataOutputStream(outRaw);

            // header is:
            // - the array length;
            // - the default element value;
            // - the number of bits per element (for non-RLE nodes);
            // - the id of the first node.
            writeLong(out, cVals);
            writeLong(out, nDefault);
            writeLong(out, cBitsPer);
            writeLong(out, aNode.length == 0 ? cVals : aNode[0].id);

            for (int i = 0; i < cNodes; ++i)
                {
                out.write(aabNode[i]);
                }

            return outRaw.toByteArray();
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }
        }

    /**
     * Decompress a ConstIntList's compressed data in its binary form into a Java BitSet.
     *
     * @param ab  the ConstIntList's compressed data in its binary form
     *
     * @return the corresponding Java BitSet
     */
    public static int[] decompress(byte[] ab)
        {
        try
            {
            ByteArrayInputStream inRaw = new ByteArrayInputStream(ab);
            DataInputStream      in    = new DataInputStream(inRaw);

            int   cVals = readPackedInt(in);
            int[] anVal = new int[cVals];
            if (cVals == 0)
                {
                return anVal;
                }

            int nDefault = readPackedInt(in);
            if (nDefault != 0)
                {
                Arrays.fill(anVal, nDefault);
                }

            int cBitsPer = readPackedInt(in);
            assert cBitsPer > 0 && cBitsPer <= 32;

            int iCur = readPackedInt(in);
            while (iCur < cVals)
                {
                RawNode node = readRawNode(in, cBitsPer);

                if (node.cVals < 0)
                    {
                    for (int i = 0, c = -node.cVals; i < c; ++i)
                        {
                        anVal[iCur + i] = node.nVal;
                        }
                    }
                else
                    {
                    int[] anNode = unpack(node.abVals, node.ofVals, node.cVals, cBitsPer);
                    for (int i = 0, c = node.cVals; i < c; ++i)
                        {
                        anVal[iCur + i] = anNode[i];
                        }
                    }

                int iAdd = node.idNext;
                if (iAdd == 0)
                    {
                    break;
                    }

                iCur += iAdd;
                }

            return anVal;
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }
        }


    // ----- internal ------------------------------------------------------------------------------

    private static List<Node> buildNodeList(int[] an, int cMinRun, int nDefault)
        {
        // identify the runs within the array
        // build a sequence of nodes (run length encoded nodes, and compressed array nodes)
        int cVals     = an.length;
        int iFirstN   = -1;     // index of first "not picked up into a node" non-default number
        int iLastN    = -1;     // index of last "not picked up into a node" non-default number
        int nPrev     = -1;
        int cRun      = 0;
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < cVals; ++i)
            {
            int n = an[i];
            if (n == nPrev)
                {
                ++cRun;
                }
            else
                {
                if (cRun >= cMinRun)
                    {
                    createNodes(list, an, i, nPrev, cRun, cMinRun, iFirstN, iLastN, nDefault);
                    iFirstN = -1;
                    iLastN  = -1;
                    }
                cRun = 1;
                }

            if (n != nDefault)
                {
                if (iFirstN < 0)
                    {
                    iFirstN = i;
                    }
                iLastN = i;
                }

            nPrev = n;
            }
        createNodes(list, an, cVals, nPrev, cRun, cMinRun, iFirstN, iLastN, nDefault);

        return list;
        }

    private static void createNodes(List<Node> list, int[] an, int i, int nRun, int cRun, int cMinRun,
                                    int iFirstN, int iLastN, int nDefault)
        {
        if (cRun >= cMinRun)
            {
            int iRun = i - cRun;

            // the last `cRun` values form a run, so we will create an RLE node for it, but
            // first we need to create an array node in front of it if there were any values
            // that appeared before the run that we did not previously capture in a node
            if (iFirstN >= 0 && iFirstN < iRun)
                {
                // only take numbers up to the beginning of the run (and don't keep any trailing
                // default values)
                if (iLastN >= iRun)
                    {
                    iLastN = iRun - 1;
                    while (iLastN > iFirstN && an[iLastN] == nDefault)
                        {
                        --iLastN;
                        }
                    }

                Node node   = new Node();
                node.rle    = false;
                node.id     = iFirstN;
                node.vals   = an;
                node.ofVals = iFirstN;
                node.cVals  = iLastN - iFirstN + 1;
                list.add(node);
                }

            if (an[iRun] != nDefault)
                {
                // create the RLE node
                Node node  = new Node();
                node.rle   = true;
                node.id    = iRun;
                node.val   = nRun;
                node.cVals = cRun;
                list.add(node);
                }
            }
        else if (iFirstN >= 0)
            {
            // create an array node
            Node node   = new Node();
            node.rle    = false;
            node.id     = iFirstN;
            node.vals   = an;
            node.ofVals = iFirstN;
            node.cVals  = iLastN - iFirstN + 1;
            list.add(node);
            }
        }

    private static void ensureFastNode(List<Node> list, int cFast, int cMinRun, int[] an)
        {
        // make sure that the initial block covers the entire "fast" range as a single block
        if (cFast > 0)
            {
            // figure out how many nodes have to be combined to create the "fast" node
            int cNodes = 0;
            for (Node node : list)
                {
                if (node.id >= cFast)
                    {
                    break;
                    }

                ++cNodes;
                }

            if (cNodes <= 1)
                {
                return;
                }

            // if the last node to combine to make the fast node is an RLE node, then see if the RLE
            // node represents a run that is long enough to be split into a portion that goes into
            // the "fast" node, and a portion that stays as an RLE node
            Node nodeLast = list.get(cNodes - 1);
            int iFirst = list.get(0).id;
            int iLast = nodeLast.id + nodeLast.cVals - 1;
            if (nodeLast.rle && iLast - cFast > cMinRun)
                {
                iLast = cFast - 1;
                int cAdjust = cFast - nodeLast.id;
                assert cAdjust > 0;
                nodeLast.id    += cAdjust;
                nodeLast.cVals -= cAdjust;
                --cNodes;
                }

            // drop the extra nodes that we're collapsing into the fast node
            while (cNodes > 1)
                {
                list.remove(0);
                --cNodes;
                }

            // build the fast node and use it in place of the first node
            Node nodeFast   = new Node();
            nodeFast.id     = iFirst;
            nodeFast.vals   = an;
            nodeFast.ofVals = iFirst;
            nodeFast.cVals  = iLast - iFirst + 1;
            nodeFast.rle    = false;
            list.set(0, nodeFast);
            }
        }

    private static byte[][] toBytes(Node[] aNode, int cBitsPer)
        {
        // turn the nodes into bytes
        int      cNodes  = aNode.length;
        byte[][] aabNode = new byte[cNodes][];

        Node nodeNext = null;
        for (int i = cNodes - 1; i >= 0; --i)
            {
            Node node = aNode[i];

            RawNode nodeRaw = new RawNode();
            assert nodeNext == null || nodeNext.id > node.id;
            // REVIEW CP: should not be 0, should be size (i.e. index of first non-existent int beyond end of array)
            //            it works now due to the "ofNext >= ab.length" in "currentNode()" method
            nodeRaw.idNext  = nodeNext == null ? 0 : nodeNext.id - node.id;

            if (node.rle)
                {
                nodeRaw.cVals = -node.cVals;
                nodeRaw.nVal  = node.val;
                }
            else
                {
                nodeRaw.cVals  = node.cVals;
                nodeRaw.abVals = pack(node.vals, node.ofVals, node.cVals, cBitsPer);
                }

            Node nodeJmp = node.jmp;
            if (nodeJmp != null)
                {
                int iJmp = findNode(aNode, nodeJmp);
                assert iJmp > i;
                nodeRaw.idJmp = nodeJmp.id - node.id;
                nodeRaw.ofJmp = calcSkip(aabNode, i, nodeRaw, iJmp);
                }

            aabNode[i] = toBytes(nodeRaw, cBitsPer);

            nodeNext = node;
            }

        return aabNode;
        }


    // ----- inner class: Node ---------------------------------------------------------------------

    private static class Node
        {
        int     id;
        boolean rle;
        int     cVals;
        int     val;
        int[]   vals;
        int     ofVals;
        Node    jmp;
        Node    next;
        }

    private static int findNode(Node[] aNode, Node node)
        {
        for (int i = 0, c = aNode.length; i < c; ++i)
            {
            if (aNode[i] == node)
                {
                return i;
                }
            }
        throw new IllegalStateException();
        }

    private static void createSkips(Node[] aNode, int iFirst, int iLast)
        {
        int cNodes = iLast - iFirst + 1;
        if (cNodes <= 2)
            {
            return;
            }

        Node node = aNode[iFirst];
        assert node.jmp == null;

        int ofJmp = Integer.highestOneBit(cNodes-1);
        node.jmp = aNode[iFirst + ofJmp];

        createSkips(aNode, iFirst + 1, iFirst + ofJmp - 1);
        createSkips(aNode, iFirst + ofJmp, iLast);
        }


    // ----- inner class: RawNode ------------------------------------------------------------------

    private static class RawNode
        {
        int     idJmp;
        int     ofJmp;
        int     idNext;
        int     cVals;
        int     nVal;       // run length node only
        byte[]  abVals;     // value array node only
        int     ofVals;
        }

    private static RawNode readRawNode(DataInputStream in, int cBitsPer)
            throws IOException
        {
        RawNode node = new RawNode();

        node.idJmp = readPackedInt(in);
        if (node.idJmp != 0)
            {
            node.ofJmp = readPackedInt(in);
            }

        node.idNext = readPackedInt(in);

        node.cVals = readPackedInt(in);
        if (node.cVals < 0)
            {
            // the run-length node form adds:
            // - the run length (encoded as a negative value to indicate RLE)
            // - the run value
            node.nVal = readPackedInt(in);
            }
        else
            {
            // the array node form adds:
            // - the number of values;
            // - the bytes necessary to hold those values
            int cb = (node.cVals * cBitsPer + 7) / 8;
            node.abVals = new byte[cb];
            in.readFully(node.abVals);
            }

        return node;
        }

    private static int fromBytes(RawNode node, byte[] ab, int of, int cBitsPer)
        {
        int  ofOrig  = of;
        long lIntVal = unpackInt(ab, of); of += (int) (lIntVal >>> 32);
        node.idJmp = (int) lIntVal;

        if (node.idJmp != 0)
            {
            lIntVal = unpackInt(ab, of); of += (int) (lIntVal >>> 32);
            node.ofJmp = (int) lIntVal;
            }

        lIntVal = unpackInt(ab, of); of += (int) (lIntVal >>> 32);
        node.idNext = (int) lIntVal;

        lIntVal = unpackInt(ab, of); of += (int) (lIntVal >>> 32);
        node.cVals = (int) lIntVal;
        if (node.cVals < 0)
            {
            // the run-length node form adds:
            // - the run length (encoded as a negative value to indicate RLE)
            // - the run value
            lIntVal = unpackInt(ab, of); of += (int) (lIntVal >>> 32);
            node.nVal = (int) lIntVal;
            }
        else
            {
            // the array node form adds:
            // - the number of values;
            // - the bytes necessary to hold those values
            int cb = (node.cVals * cBitsPer + 7) / 8;
            node.abVals = ab;
            node.ofVals = of;
            of += cb;
            }

        return of - ofOrig;
        }

    private static byte[] toBytes(RawNode node, int cBitsPer)
        {
        // - a skip byte id (encoded as a relative value to this node's implicit byte id)
        // - the number of bytes to skip forward (exists if the skip byte id is non-zero)
        // - the next byte id (encoded as a relative value to this node's implicit byte id)
        //
        // for RLE:
        // - the run length (encoded as a negative value to indicate RLE)
        // - the run value
        //
        // the array node form adds:
        // - the number of values
        // - the bytes necessary to hold the LSBs of the values
        ByteArrayOutputStream outRaw = new ByteArrayOutputStream();
        DataOutputStream      out    = new DataOutputStream(outRaw);
        try
            {
            writeLong(out, node.idJmp);
            if (node.idJmp > 0)
                {
                writeLong(out, node.ofJmp);
                }
            writeLong(out, node.idNext);
            writeLong(out, node.cVals);
            if (node.cVals < 0)
                {
                writeLong(out, node.nVal);
                }
            else
                {
                int cb = (node.cVals * cBitsPer + 7) / 8;
                out.write(node.abVals, node.ofVals, cb);
                }
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }
        return outRaw.toByteArray();
        }

    private static int calcSkip(byte[][] aabNode, int iFrom, RawNode nodeFrom, int iTo)
        {
        assert iTo > iFrom + 1;

        // first we have to skip over the remainder of the "from" node
        int cb = packedLength(nodeFrom.idNext)
               + packedLength(nodeFrom.cVals);

        if (nodeFrom.cVals < 0)
            {
            cb += packedLength(nodeFrom.nVal);
            }
        else
            {
            assert nodeFrom.ofVals == 0;
            cb += nodeFrom.abVals.length;
            }

        // then we have to skip over any nodes in between the "from" and "to" nodes
        for (int i = iFrom + 1; i < iTo; ++i)
            {
            cb += aabNode[i].length;
            }

        return cb;
        }


    // ----- packed array --------------------------------------------------------------------------

    private static byte[] pack(int[] an, int of, int cn, int cBitsPer)
        {
        int    cBytes = (cn * cBitsPer + 7) / 8;
        byte[] ab     = new byte[cBytes];

        for (int i = 0; i < cn; ++i)
            {
            int n     = an[of+i];
            int ofBit = i * cBitsPer;
            while (n != 0)
                {
                int ofByte = ofBit / 8;
                int nByte  = ab[ofByte];

                // how many bits will fit into this byte?
                int cStore  = 8 - (ofBit & 0x7);
                int ofStore = 8 - cStore;

                nByte |= n << ofStore;
                ab[ofByte] = (byte) nByte;

                n   >>>= cStore;
                ofBit += cStore;
                }
            }

        return ab;
        }

    private static int[] unpack(byte[] ab, int of, int cVals, int cBitsPer)
        {
        int[] an = new int[cVals];
        for (int i = 0; i < cVals; ++i)
            {
            an[i] = unpackOne(ab, of, cBitsPer, i);
            }

        return an;
        }

    private static int unpackOne(byte[] ab, int of, int cBitsPer, int idVal)
        {
        int n = 0;

        int ofReadBit   = idVal * cBitsPer;
        int cBitsRemain = cBitsPer;
        while (cBitsRemain > 0)
            {
            int b = ab[of + ofReadBit / 8];

            int ofPartBit = ofReadBit & 0x7;
            int cPartBits = Math.min(8 - ofPartBit, cBitsRemain);

            n |= ((b >>> ofPartBit) & ((1 << cPartBits) - 1)) << cBitsPer - cBitsRemain;

            ofReadBit   += cPartBits;
            cBitsRemain -= cPartBits;
            }

        return n;
        }

// Test code for pack/unpack:
//
//    public static void main(String[] args)
//        {
//        var rnd = new java.util.Random();
//        for (int iTest = 0; iTest < 1000000; ++iTest)
//            {
//            if (iTest % 1000 == 0)
//                {
//                System.out.println("test #" + iTest);
//                }
//            int   cBitsPer = 1+rnd.nextInt(31);
//            int   nMax  = (1 << cBitsPer) - 1;
//            int   cVals = 1+rnd.nextInt(100000);
//            int[] anVal = new int[cVals];
//            for (int i = 0; i < cVals; ++i)
//                {
//                anVal[i] = rnd.nextInt(nMax);
//                }
//
//            byte[] ab     = pack(anVal, 0, cVals, cBitsPer);
//            int[]  anVal2 = unpack(ab, 0, cVals, cBitsPer);
//            String sErr   = null;
//            if (anVal.length != anVal2.length)
//                {
//                sErr = "Different lengths: orig=" + anVal.length + ", new=" + anVal2.length;
//                }
//            else
//                {
//                for (int i = 0; i < cVals; ++i)
//                    {
//                    if (anVal[i] != anVal2[i])
//                        {
//                        sErr = "Value " + i + " differs: orig=" + anVal[i] + ", new=" + anVal2[i];
//                        break;
//                        }
//                    }
//                }
//            if (sErr != null)
//                {
//                throw new IllegalStateException("test failed for array=" + Arrays.asList(anVal) + "\n" + sErr);
//                }
//            }
//        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * The compressed form.
     */
    private final byte[] m_ab;
    }