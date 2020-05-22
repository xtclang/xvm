package org.xvm.util;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.BitSet;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.PackedInteger.unpackInt;
import static org.xvm.util.PackedInteger.writeLong;


/**
 * An immutable BitSet implementation that stores a compressed form of the BitSet in a byte array.
 * <p/>
 * The data structure is somewhat reminiscent of a skip-list, and somewhat reminiscent of a binary
 * tree, so it could be called the "skip-blist" format.
 * <p/>
 * The goal of the design was to create a relatively compact form of the BitSet data structure that
 * could be used to store persistent bit set images for efficient consumption by the Ecstasy class
 * library. Specifically, a significant amount of tabular information for the Unicode standard
 * needed to be encoded (as opposed to <i>hard-coded</i>) in a manner that would efficiently
 * support:
 *
 * <ul><li>Very fast access to the beginning of the data (because that is where the common ASCII
 *         codes are located;
 * </li><li>O(log(n)) access to any specified bit;
 * </li><li>Ability to iterate sequentially ("in order");
 * </li><li>Well-compressed if the bit set is either very sparse or very full.
 * </li></ul>
 * <p/>
 * The format is a variable-length header followed by a sequence of variable-length nodes. Integer
 * values encoded within the formal all use the {@link PackedInteger} format. The header contains:
 * <ul><li>The magnitude of this field indicates the cardinality of the bit set, which is the count
 *         of '1' bits in the bit set; the integer value 0 indicates an empty bit-set, and thus also
 *         indicates EOF (i.e. no further header or nodes will exist); a negative value indicates
 *         that the <b>inverse</b> of the bit-set was encoded, while a positive value indicates a
 *         normal encoding;
 * </li><li>In the case of a negative value in the cardinality field, then the header contains a
 *          second field which is the original (non-inverse) bit-set's <i>length</i> in bits, which
 *          is one more than the index of the last '1' bit in the non-inverse bit-set.
 * </li></ul>
 * <p/>
 * The format is a sequence of variable-length nodes. Each node is composed of:
 * <ul><li>An <i>implicit</i> byte id for the node (a byte id is the index into a <i>conceptual</i>
 *         byte array that backs the bit set);
 * </li><li>A <i>skip</i> byte id (encoded as a relative value to this node's implicit byte id) for
 *          the node that can be skipped forward to in the bit set, or zero to indicate that no skip
 *          forward option is available from this node;
 * </li><li>The number of bytes to skip forward (this field only exists if the skip byte id is
 *          non-zero);
 * </li><li>A <i>next</i> byte id (encoded as a relative value to this node's implicit byte id) for
 *          the node that immediately follows the current node, or zero to indicate that this is the
 *          last node in the bit set (i.e. EOF);
 * </li><li>The number of bytes of bit-encoded data in this node (which may be 0 in the first node);
 * </li><li>The sequence of bytes of bit-encoded data.
 * </li></ul>
 * <p/>
 * The encoding of the "skip" information is designed to achieve O(log(n)) behavior, by allowing a
 * skip forward of approximately half of the remaining nodes. For example, in a bit set that is
 * encoded in 28 nodes [0..27], the following table shows each node and its skip-to-node option:
 * <pre><code>
 * node-id: 00 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27
 * skip-to: 16 09 06 05       08       13 12       15       24 21 20       23       28 27
 * </code></pre>
 * (Note that neither the node number nor the skip-to node number are actually present in the
 * encoding.)
 */
public class ConstBitSet
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new ConstBitSet from a bit-set.
     *
     * @param bs  the bit-set to compress
     */
    public ConstBitSet(BitSet bs)
        {
        this(compress(bs));
        }

    /**
     * Construct a new ConstBitSet from the already-compressed form.
     *
     * @param ab  the compressed form of a bit-set
     */
    public ConstBitSet(byte[] ab)
        {
        assert ab != null;
        m_ab = ab;
        }


    // ----- accessors --------------------------------------------------------------------------

    /**
     * @return a Java BitSet containing a copy of the same bit-set data
     */
    public BitSet toBitSet()
        {
        return decompress(m_ab);
        }

    /**
     * @return a copy of this ConstBitSet's compressed data in its binary form
     */
    public byte[] getBytes()
        {
        return m_ab.clone();
        }


    // ----- BitSet API ----------------------------------------------------------------------------

    /**
     * See {@link BitSet#length()}
     */
    public int length()
        {
        long lIntVal = unpackInt(m_ab, 0);
        int  nCount  = (int) lIntVal;
        if (nCount == 0)
            {
            return 0;
            }
        int ofCur = (int) (lIntVal >>> 32);

        if (nCount < 0)
            {
            // length is encoded as the second field for inverse bit-sets
            return (int) unpackInt(m_ab, ofCur);
            }

        // unfortunately, we have to jump & walk all the way to the end to find the length
        int idCur = 0;
        while (true)
            {
            lIntVal    = unpackInt(m_ab, ofCur);
            ofCur     += (int) (lIntVal >>> 32);
            int idSkip = (int) lIntVal;

            // always skip forward, unless there is no skip
            if (idSkip == 0)
                {
                lIntVal    = unpackInt(m_ab, ofCur);
                ofCur     += (int) (lIntVal >>> 32);
                int idNext = (int) lIntVal;

                lIntVal    = unpackInt(m_ab, ofCur);
                ofCur     += (int) (lIntVal >>> 32);
                int cBytes = (int) lIntVal;

                if (idNext == 0)
                    {
                    // we are on the last node; the byte array can only be empty on the first node,
                    // which won't even exist for an empty bit-set; therefore, the byte array is not
                    // empty
                    assert cBytes > 0;
                    int bLast = m_ab[ofCur + cBytes - 1] & 0xFF;

                    // no empty trailing byte is allowed; compression would have removed it
                    int nHigh = Integer.highestOneBit(bLast);
                    assert nHigh != 0;

                    return (idCur + cBytes - 1) * 8 + Integer.numberOfTrailingZeros(nHigh) + 1;
                    }
                else
                    {
                    // walk forward to the next node
                    idCur += idNext;
                    ofCur += cBytes;
                    }
                }
            else
                {
                // skip forward to the next node
                lIntVal    = unpackInt(m_ab, ofCur);
                ofCur     += (int) (lIntVal >>> 32);
                int ofSkip = (int) lIntVal;

                idCur += idSkip;
                ofCur += ofSkip;
                }
            }
        }

    /**
     * See {@link BitSet#isEmpty()}
     */
    public boolean isEmpty()
        {
        return cardinality() == 0;
        }

    /**
     * See {@link BitSet#cardinality()}
     */
    public int cardinality()
        {
        long lCount = unpackInt(m_ab, 0);
        int  nCount = (int) lCount;
        return nCount < 0 ? -nCount : nCount;
        }

    /**
     * See {@link BitSet#size()}
     */
    public int size()
        {
        return m_ab.length * 8;
        }

    /**
     * See {@link BitSet#get(int)}
     */
    public boolean get(int iBit)
        {
        long lIntVal = unpackInt(m_ab, 0);
        int  nCount  = (int) lIntVal;
        if (nCount == 0)
            {
            return false;
            }
        int ofCur = (int) (lIntVal >>> 32);

        boolean fInverse    = false;
        int     cInverseLen = 0;
        if (nCount < 0)
            {
            fInverse    = true;
            lIntVal     = unpackInt(m_ab, ofCur);
            ofCur      += (int) (lIntVal >>> 32);
            cInverseLen = (int) lIntVal;
            }

        int iByte = iBit >>> 3;
        int idCur = 0;
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

                if (iByte >= idCur + idSkip)
                    {
                    idCur += idSkip;
                    ofCur += ofSkip;
                    continue;
                    }
                }

            // three possibilities for the bit's position at this point:
            // 1) between the current point and the end of bytes present in this node
            // 2) after this node but before the next node (or there is no next node)
            // 3) somewhere after the start of the next node (i.e. only if there is a next node)
            lIntVal    = unpackInt(m_ab, ofCur);
            ofCur     += (int) (lIntVal >>> 32);
            int idNext = (int) lIntVal;

            lIntVal    = unpackInt(m_ab, ofCur);
            ofCur     += (int) (lIntVal >>> 32);
            int cBytes = (int) lIntVal;

            if (idNext != 0 && iByte >= idCur + idNext)
                {
                idCur += idNext;
                ofCur += cBytes;
                continue;
                }

            if (iByte < idCur + cBytes)
                {
                assert iByte >= idCur;
                return fInverse != ((m_ab[ofCur + iByte] & (1 << (iBit & 0x7))) != 0);
                }

            return fInverse && iBit < cInverseLen;
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Create an inverse of the passed BitSet.
     *
     * @param bs  a BitSet
     *
     * @return a new BitSet that is an inverse of the passed BitSet
     */
    public static BitSet invert(BitSet bs)
        {
        int    length    = bs.length();
        BitSet bsInverse = new BitSet(length);
        for (int id = bs.nextClearBit(0); id < length; id = bs.nextClearBit(id+1))
            {
            bsInverse.set(id);
            }
        return bsInverse;
        }

    /**
     * Compress a BitSet into the ConstBitSet compressed format.
     *
     * @param bs  the Java BitSet to compress
     *
     * @return the ConstBitSet's compressed data in its binary form
     */
    public static byte[] compress(BitSet bs)
        {
        byte[] ab;
        try
            {
            ByteArrayOutputStream outRaw = new ByteArrayOutputStream();
            DataOutputStream      out    = new DataOutputStream(outRaw);

            int c = bs.cardinality();
            writeLong(out, c);
            if (c != 0)
                {
                writeCompressedNodes(out, bs);
                }
            ab = outRaw.toByteArray();

            if (c != 0)
                {
                outRaw = new ByteArrayOutputStream();
                out    = new DataOutputStream(outRaw);

                BitSet bsInverse = invert(bs);
                writeLong(out, -c);
                writeLong(out, bs.length());
                writeCompressedNodes(out, bsInverse);

                byte[] abInverse = outRaw.toByteArray();
                if (abInverse.length < ab.length)
                    {
                    ab = abInverse;
                    }
                }
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }

        return ab;
        }

    /**
     * Decompress a ConstBitSet's compressed data in its binary form into a Java BitSet.
     *
     * @param ab  the ConstBitSet's compressed data in its binary form
     *
     * @return the corresponding Java BitSet
     */
    public static BitSet decompress(byte[] ab)
        {
        BitSet bs = new BitSet();
        try
            {
            ByteArrayInputStream inRaw = new ByteArrayInputStream(ab);
            DataInputStream      in    = new DataInputStream(inRaw);

            int c = readPackedInt(in);
            int length = 0;
            if (c != 0)
                {
                boolean fInverse = false;
                if (c < 0)
                    {
                    length   = readPackedInt(in);
                    c        = -c;
                    fInverse = true;
                    }

                int id = 0;
                while (true)
                    {
                    RawNode node = readRawNode(in);

                    byte[] bits = node.bits;
                    assert bits != null && (id == 0 || bits.length > 0);
                    for (int iByte = 0, cBytes = bits.length; iByte < cBytes; ++iByte)
                        {
                        int curByte = bits[iByte] & 0xFF;
                        for (int iBit = 0; iBit < 8; ++iBit)
                            {
                            boolean curBit = (curByte & (1 << iBit)) != 0;
                            if (curBit)
                                {
                                bs.set(id * 8 + iByte * 8 + iBit);
                                }
                            }
                        }

                    int iAdd = node.idNext;
                    if (iAdd == 0)
                        {
                        break;
                        }

                    id += iAdd;
                    }

                if (fInverse)
                    {
                    int inverseLength = bs.length();
                    assert inverseLength <= length;
                    bs = invert(bs);

                    for (int iBit = inverseLength; iBit < length; ++iBit)
                        {
                        bs.set(iBit);
                        }
                    }

                assert c == bs.cardinality();
                }
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }

        return bs;
        }


    // ----- internal ------------------------------------------------------------------------------

    private static void writeCompressedNodes(DataOutputStream out, BitSet bs)
            throws IOException
        {
        assert bs.cardinality() > 0;
        byte[] abAll  = bs.toByteArray();
        int    cbAll  = abAll.length;
        int    iFirst = 0;
        int    iLast  = -1;

        ArrayList<Node> listNodes = new ArrayList<>();
        boolean         fInNode   = true;
        int             cSkip     = 0;
        for (int iCur = 0; iCur < cbAll; ++iCur)
            {
            byte b = abAll[iCur];
            if (b == 0)
                {
                ++cSkip;
                if (fInNode && cSkip >= 6)
                    {
                    listNodes.add(makeNode(abAll, iFirst, iLast));
                    fInNode = false;
                    }
                }
            else
                {
                if (!fInNode)
                    {
                    fInNode = true;
                    iFirst  = iCur;
                    }
                iLast = iCur;
                cSkip = 0;
                }
            }

        if (fInNode && iLast >= iFirst)
            {
            // create a node
            listNodes.add(makeNode(abAll, iFirst, iLast));
            }

        // link "next node" pointers
        Node[] aNode  = listNodes.toArray(new Node[0]);
        int    cNodes = aNode.length;
        for (int i = 1; i < cNodes; ++i)
            {
            aNode[i-1].next = aNode[i];
            }

        // link "skip node" pointers
        createSkips(aNode, 0, aNode.length-1);

        // turn the nodes into bytes
        byte[][] aabNode = new byte[cNodes][];
        Node nodeNext = null;
        for (int i = cNodes - 1; i >= 0; --i)
            {
            Node    node    = aNode[i];
            RawNode nodeRaw = new RawNode();
            nodeRaw.idNext  = nodeNext == null ? 0 : nodeNext.id - node.id;
            nodeRaw.bits    = node.bits;
            assert nodeRaw.bits != null;

            Node nodeJmp = node.jmp;
            if (nodeJmp != null)
                {
                int iJmp = findNode(aNode, nodeJmp);
                assert iJmp > i;
                nodeRaw.idJmp = nodeJmp.id - node.id;
                nodeRaw.ofJmp = calcSkip(aabNode, i, nodeRaw, iJmp);
                }

            aabNode[i] = toBytes(nodeRaw);

            nodeNext = node;
            }

        for (int i = 0; i < cNodes; ++i)
            {
            out.write(aabNode[i]);
            }
        }


    // ----- inner class: Node ---------------------------------------------------------------------

    private static class Node
        {
        int    id;
        Node   jmp;
        Node   next;
        byte[] bits;
        }

    private static Node makeNode(byte[] ab, int iFirst, int iLast)
        {
        int cbNode = iLast - iFirst + 1;
        assert cbNode >= 0;
        byte[] abNode = new byte[cbNode];
        System.arraycopy(ab, iFirst, abNode, 0, cbNode);
        Node node = new Node();
        node.id   = iFirst;
        node.bits = abNode;
        return node;
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
        Node node = aNode[iFirst];
        assert node.jmp == null;

        int cNodes = iLast - iFirst + 1;
        if (cNodes <= 2)
            {
            return;
            }

        int ofJmp = Integer.highestOneBit(cNodes-1);
        node.jmp = aNode[iFirst + ofJmp];

        createSkips(aNode, iFirst + 1, iFirst + ofJmp - 1);
        createSkips(aNode, iFirst + ofJmp, iLast);
        }


    // ----- inner class: RawNode ------------------------------------------------------------------

    private static class RawNode
        {
        int    idJmp;
        int    ofJmp;
        int    idNext;
        byte[] bits;
        }

    private static RawNode readRawNode(DataInputStream in)
        throws IOException
        {
        RawNode node = new RawNode();

        node.idJmp = readPackedInt(in);
        if (node.idJmp != 0)
            {
            node.ofJmp = readPackedInt(in);
            }

        node.idNext = readPackedInt(in);

        int cb = readPackedInt(in);
        byte[] ab = new byte[cb];
        in.readFully(ab);
        node.bits = ab;

        return node;
        }

    private static byte[] toBytes(RawNode node)
        {
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
            writeLong(out, node.bits.length);
            out.write(node.bits);
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
        ByteArrayOutputStream outRaw = new ByteArrayOutputStream();
        DataOutputStream      out    = new DataOutputStream(outRaw);
        try
            {
            writeLong(out, nodeFrom.idNext);
            writeLong(out, nodeFrom.bits.length);
            }
        catch (IOException e)
            {
            throw new RuntimeException(e);
            }
        int cb = outRaw.size() + nodeFrom.bits.length;

        // then we have to skip over any nodes in between the "from" and "to" nodes
        for (int i = iFrom + 1; i < iTo; ++i)
            {
            cb += aabNode[i].length;
            }

        return cb;
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * The compressed form.
     */
    private byte[] m_ab;
    }
