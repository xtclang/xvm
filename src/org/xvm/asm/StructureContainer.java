package org.xvm.asm;


import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * TODO delete
 *
 * @author cp 2016.09.22
 */
public abstract class StructureContainer
    {
    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Create a new TypeConstant whose exact type will eventually be resolved.
     *
     * @param sType  the String representation of the type
     *
     * @return a new UnresolvedTypeConstant
     */
    public UnresolvedTypeConstant createUnresolvedType(String sType)
        {
        return getConstantPool().createUnresolvedTypeConstant(sType);
        }

    /**
     * Helper method to read a collection of XVM sub-structures from the DataInput stream.
     *
     * @param in  the DataInput containing the XVM structures
     *
     * @return a List of XvmStructure objects
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected List<? extends XvmStructure> disassembleSubStructureCollection(DataInput in)
            throws IOException
        {
        int c = readMagnitude(in);
        if (c == 0)
            {
            return Collections.EMPTY_LIST;
            }

        XvmStructure[] astruct = new XvmStructure[c];
        for (int i = 0; i < c; ++i)
            {
            astruct[i] = disassembleSubStructure(in);
            }
        return Arrays.asList(astruct);
        }

    /**
     * Helper method to read an XVM sub-structure from the DataInput stream.
     *
     * @param in  the DataInput containing the XVM structure
     *
     * @return an XvmStructure
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected XvmStructure disassembleSubStructure(DataInput in)
            throws IOException
        {
        // read in the identity of this file structure
        Constant constId = getConstantPool().getConstant(readMagnitude(in));

        if (constId.getFormat().isLengthEncoded())
            {
            // skip over length encoding
            readMagnitude(in);
            }

        XvmStructure structSub = constId.instantiate(this);
        structSub.disassemble(in);
        return structSub;
        }

    /**
     * Helper method to read a collection of type parameters.
     *
     * @param in  the DataInput containing the type parameters
     *
     * @return null if there are no type parameters, otherwise a map from CharStringConstant to the
     *         type constraint for each parameter
     *
     * @throws IOException  if an I/O exception occurs during disassembly from the provided
     *                      DataInput stream, or if there is invalid data in the stream
     */
    protected ListMap<CharStringConstant, TypeConstant> disassembleTypeParams(DataInput in)
            throws IOException
        {
        int c = readMagnitude(in);
        if (c <= 0)
            {
            assert c == 0;
            return null;
            }

        final ListMap<CharStringConstant, TypeConstant> map = new ListMap<>();
        final ConstantPool pool = getConstantPool();
        for (int i = 0; i < c; ++i)
            {
            CharStringConstant constName = (CharStringConstant) pool.getConstant(readIndex(in));
            TypeConstant       constType = (TypeConstant)       pool.getConstant(readIndex(in));
            assert !map.containsKey(constName);
            map.put(constName, constType);
            }
        return map;
        }

    /**
     * Helper method to write an XVM sub-structure to the DataInput stream.
     *
     * @param coll  the collection of XVM structure to assemble
     * @param out   the DataOutput to write the XVM structure to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assembleSubStructureCollection(Collection<? extends XvmStructure> coll, DataOutput out)
            throws IOException
        {
        int c = coll.size();
        writePackedLong(out, c);
        if (c == 0)
            {
            return;
            }

        XvmStructure[] astruct = coll.toArray(new XvmStructure[c]);
        Arrays.sort(astruct, IDENTITY_CONSTANT_COMPARATOR);

        for (int i = 0; i < c; ++i)
            {
            assembleSubStructure(astruct[i], out);
            }
        }

    /**
     * Helper method to write an XVM sub-structure to the DataInput stream.
     *
     * @param structSub  the XVM structure to assemble
     * @param out        the DataOutput to write the XVM structure to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assembleSubStructure(XvmStructure structSub, DataOutput out)
            throws IOException
        {
        Constant constId = structSub.getIdentityConstant();
        writePackedLong(out, constId.getPosition());

        if (constId.getFormat().isLengthEncoded())
            {
            // length-encode the sub-structure (allowing a reader to optionally
            // skip over it)
            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            structSub.assemble(new DataOutputStream(outBuf));
            byte[] ab = outBuf.toByteArray();
            writePackedLong(out, ab.length);
            out.write(ab);
            }
        else
            {
            structSub.assemble(out);
            }
        }

    /**
     * Helper method to write type parameters to the DataOutput stream.
     *
     * @param map  the type parameters
     * @param out  the DataOutput to write the XVM structure to
     *
     * @throws IOException  if an I/O exception occurs during assembly to the provided DataOutput
     *                      stream
     */
    protected void assembleTypeParams(ListMap<CharStringConstant, TypeConstant> map, DataOutput out)
            throws IOException
        {
        int c = map == null ? 0 : map.size();
        writePackedLong(out, c);

        if (c == 0)
            {
            return;
            }

        for (Map.Entry<CharStringConstant, TypeConstant> entry : map.entrySet())
            {
            writePackedLong(out, entry.getKey().getPosition());
            writePackedLong(out, entry.getValue().getPosition());
            }
        }

    /**
     * Compare two lazily instantiated maps for equality.
     *
     * @param mapThis  a map, or null
     * @param mapThat  a map, or null
     *
     * @return false iff at least one map is non-null and non-empty, and the other map is null or
     *         their contents do not match
     */
    protected static boolean equalMaps(Map mapThis, Map mapThat)
        {
        int cThis = mapThis == null ? 0 : mapThis.size();
        int cThat = mapThat == null ? 0 : mapThat.size();
        return cThis == cThat && (cThis == 0 || mapThis.equals(mapThat));
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * A Comparator that compares two XvmStructure object for sorting purposes based on their
     * identity constants.
     */
    private static final Comparator<? super XvmStructure> IDENTITY_CONSTANT_COMPARATOR = new Comparator<XvmStructure>()
        {
        @Override
        public int compare(XvmStructure o1, XvmStructure o2)
            {
            return o1.getIdentityConstant().compareTo(o2.getIdentityConstant());
            }

        @Override
        public boolean equals(Object obj)
            {
            return this == obj;
            }
        };
    }
