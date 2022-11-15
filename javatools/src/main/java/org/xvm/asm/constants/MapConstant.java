package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a constant value that contains a single map entry or an entire map of keys and values.
 */
public class MapConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a map entry.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType   the data type of the map entry
     * @param constKey    the constant value of the map key
     * @param constVal    the constant value of the map value
     */
    public MapConstant(ConstantPool pool, TypeConstant constType, Constant constKey, Constant constVal)
        {
        this(pool, Format.MapEntry, constType, new Constant[] {constKey}, new Constant[] {constVal});
        }

    /**
     * Construct a constant whose value is a map.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType   the data type of the map
     * @param map         the map of keys to values
     */
    public MapConstant(ConstantPool pool, TypeConstant constType, Map<? extends Constant, ? extends Constant> map)
        {
        this(pool, Format.Map, constType, map.keySet().toArray(NO_CONSTS), map.values().toArray(NO_CONSTS));
        }

    /**
     * Construct a constant whose value is a map entry.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param fmt         the constant format
     * @param constType   the data type of the map
     * @param aconstKey   the constant values of the map keys
     * @param aconstVal   the constant values of the map values
     */
    public MapConstant(ConstantPool pool, Format fmt, TypeConstant constType, Constant[] aconstKey, Constant[] aconstVal)
        {
        super(pool);

        if (!(fmt == Format.Map || fmt == Format.MapEntry))
            {
            throw new IllegalArgumentException("fmt required to be Map or MapEntry");
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }

        // TODO other type validation?

        if (aconstKey == null)
            {
            throw new IllegalArgumentException("keys required");
            }

        if (aconstVal == null)
            {
            throw new IllegalArgumentException("values required");
            }

        int cEntries = aconstKey.length;
        if (aconstVal.length != cEntries)
            {
            throw new IllegalArgumentException("mismatch between number of keys and values");
            }

        for (int i = 0; i < cEntries; ++i)
            {
            if (aconstKey[i] == null)
                {
                throw new IllegalArgumentException("keys required (" + i + " missing)");
                }
            if (aconstVal[i] == null)
                {
                throw new IllegalArgumentException("values required (" + i + " missing)");
                }

            // TODO other key/value validation?
            }

        m_fmt        = fmt;
        m_constType  = constType;
        m_aconstKey  = aconstKey;
        m_aconstVal  = aconstVal;
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public MapConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        int iType    = readMagnitude(in);
        int cEntries = readMagnitude(in);
        int[] aiKeys = new int[cEntries];
        int[] aiVals = new int[cEntries];
        for (int i = 0; i < cEntries; ++i)
            {
            aiKeys[i] = readMagnitude(in);
            aiVals[i] = readMagnitude(in);
            }

        m_fmt    = format;
        m_iType  = iType;
        m_aiKeys = aiKeys;
        m_aiVals = aiVals;
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_constType = (TypeConstant) pool.getConstant(m_iType);

        int[]      aiConstKey = m_aiKeys;
        int[]      aiConstVal = m_aiVals;
        int        cEntries   = aiConstKey.length;
        Constant[] aconstKey  = new Constant[cEntries];
        Constant[] aconstVal  = new Constant[cEntries];
        for (int i = 0; i < cEntries; ++i)
            {
            aconstKey[i] = pool.getConstant(aiConstKey[i]);
            aconstVal[i] = pool.getConstant(aiConstVal[i]);
            }
        m_aconstKey = aconstKey;
        m_aconstVal = aconstVal;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return m_constType;
        }

    /**
     * {@inheritDoc}
     * @return  the constant's contents as a map (with an entry being returned as a map with one
     *          entry in it)
     */
    @Override
    public Map<Constant, Constant> getValue()
        {
        return new ROMap<>(m_aconstKey, m_aconstVal);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_fmt;
        }

    @Override
    public boolean isValueCacheable()
        {
        return !m_constType.containsFormalType(true);
        }

    @Override
    public boolean containsUnresolved()
        {
        if (m_constType.containsUnresolved())
            {
            return true;
            }

        Constant[] aconstKey = m_aconstKey;
        Constant[] aconstVal = m_aconstVal;
        for (int i = 0, c = aconstKey.length; i < c; ++i)
            {
            if (aconstKey[i].containsUnresolved() || aconstVal[i].containsUnresolved())
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType);
        Constant[] aconstKey = m_aconstKey;
        Constant[] aconstVal = m_aconstVal;
        for (int i = 0, c = aconstKey.length; i < c; ++i)
            {
            visitor.accept(aconstKey[i]);
            visitor.accept(aconstVal[i]);
            }
        }

    @Override
    public MapConstant resolveTypedefs()
        {
        TypeConstant typeOld = m_constType;
        TypeConstant typeNew = typeOld.resolveTypedefs();

        // check keys
        Constant[] aconstOldKey = m_aconstKey;
        Constant[] aconstNewKey = null;
        for (int i = 0, c = aconstOldKey.length; i < c; ++i)
            {
            Constant constOldKey = aconstOldKey[i];
            Constant constNewKey = constOldKey.resolveTypedefs();
            if (constNewKey != constOldKey)
                {
                if (aconstNewKey == null)
                    {
                    aconstNewKey = aconstOldKey.clone();
                    }
                aconstNewKey[i] = constNewKey;
                }
            }

        // check values
        Constant[] aconstOldVal = m_aconstVal;
        Constant[] aconstNewVal = null;
        for (int i = 0, c = aconstOldVal.length; i < c; ++i)
            {
            Constant constOldVal = aconstOldVal[i];
            Constant constNewVal = constOldVal.resolveTypedefs();
            if (constNewVal != constOldVal)
                {
                if (aconstNewVal == null)
                    {
                    aconstNewVal = aconstOldVal.clone();
                    }
                aconstNewVal[i] = constNewVal;
                }
            }

        return typeNew == typeOld && aconstNewKey == null && aconstNewVal == null
                ? this
                : (MapConstant) getConstantPool().register(new MapConstant(
                        getConstantPool(), m_fmt, typeNew, aconstNewKey, aconstNewVal));
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof MapConstant))
            {
            return -1;
            }
        int nResult = this.m_constType.compareTo(((MapConstant) that).m_constType);
        if (nResult != 0)
            {
            return nResult;
            }

        Constant[] aconstThisKey = this.m_aconstKey;
        Constant[] aconstThatKey = ((MapConstant) that).m_aconstKey;
        Constant[] aconstThisVal = this.m_aconstVal;
        Constant[] aconstThatVal = ((MapConstant) that).m_aconstVal;
        int        cThis         = aconstThisKey.length;
        int        cThat         = aconstThatKey.length;
        for (int i = 0, c = Math.min(cThis, cThat); i < c; ++i)
            {
            nResult = aconstThisKey[i].compareTo(aconstThatKey[i]);
            if (nResult != 0)
                {
                nResult = aconstThisVal[i].compareTo(aconstThatVal[i]);
                if (nResult != 0)
                    {
                    return nResult;
                    }
                }
            }
        return cThis - cThat;
        }

    @Override
    public String getValueString()
        {
        Constant[] aKeys = m_aconstKey;
        Constant[] aVals = m_aconstVal;
        int        cKeys = aKeys.length;

        StringBuilder sb = new StringBuilder();
        sb.append("Map:{");                     // TODO also implement MapEntry

        for (int i = 0; i < cKeys; ++i)
            {
            if (i > 0)
                {
                sb.append(", ");
                }

            sb.append(aKeys[i])
              .append('=')
              .append(aVals[i]);
            }

        sb.append("}");
        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType = (TypeConstant) pool.register(m_constType);
        m_aconstKey = registerConstants(pool, m_aconstKey);
        m_aconstVal = registerConstants(pool, m_aconstVal);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constType.getPosition());

        Constant[] aconstKey = m_aconstKey;
        Constant[] aconstVal = m_aconstVal;
        int        cEntries  = aconstKey.length;
        writePackedLong(out, cEntries);
        for (int i = 0; i < cEntries; ++i)
            {
            writePackedLong(out, aconstKey[i].getPosition());
            writePackedLong(out, aconstVal[i].getPosition());
            }
        }

    @Override
    public String getDescription()
        {
        return "map-length=" + m_aconstKey.length;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constType,
               Hash.of(m_aconstKey,
               Hash.of(m_aconstVal)));
        }


    // ----- inner class: ROEntry ------------------------------------------------------------------

    /**
     * A simple, read-only Map Entry.
     */
    public static class ROEntry<K,V>
            extends AbstractMap.SimpleEntry<K,V>
        {
        public ROEntry(K key, V value)
            {
            super(key, value);
            }

        public V setValue(V value)
            {
            throw new UnsupportedOperationException();
            }
        }


    // ----- inner class: ROMap --------------------------------------------------------------------

    /**
     * A simple, read-only, fixed-order Map.
     */
    public static class ROMap<K,V>
            extends AbstractMap<K,V>
        {
        public ROMap(K[] ak, V[] av)
            {
            assert ak != null && av != null && ak.length == av.length;
            this.ak = ak;
            this.av = av;
            }

        @Override
        public int size()
            {
            return ak.length;
            }

        @Override
        public boolean containsKey(Object key)
            {
            return find(key) >= 0;
            }

        @Override
        public V get(Object key)
            {
            int i = find(key);
            return i < 0 ? null : av[i];
            }

        @Override
        public Set<K> keySet()
            {
            Set<K> set = setKeys;
            if (set == null)
                {
                setKeys = set = new AbstractSet<K>()
                    {
                    @Override
                    public int size()
                        {
                        return ak.length;
                        }

                    @Override
                    public boolean contains(Object o)
                        {
                        return find(o) >= 0;
                        }

                    @Override
                    public Iterator<K> iterator()
                        {
                        return new Iterator<K>()
                            {
                            @Override
                            public K next()
                                {
                                if (hasNext())
                                    {
                                    return ak[iNext++];
                                    }
                                throw new NoSuchElementException();
                                }

                            @Override
                            public boolean hasNext()
                                {
                                return iNext < ak.length;
                                }

                            int iNext = 0;
                            };
                        }
                    };
                }
            return set;
            }

        @Override
        public Set<Entry<K,V>> entrySet()
            {
            Set<Entry<K,V>> set = setEntries;
            if (set == null)
                {
                setEntries = set = new AbstractSet<Entry<K,V>>()
                    {
                    @Override
                    public int size()
                        {
                        return ak.length;
                        }

                    @Override
                    public Iterator<Entry<K,V>> iterator()
                        {
                        return new Iterator<Entry<K,V>>()
                            {
                            @Override
                            public Entry<K, V> next()
                                {
                                if (hasNext())
                                    {
                                    return new ROEntry<>(ak[iNext], av[iNext++]);
                                    }
                                throw new NoSuchElementException();
                                }

                            @Override
                            public boolean hasNext()
                                {
                                return iNext < ak.length;
                                }

                            int iNext = 0;
                            };
                        }
                    };
                }
            return set;
            }

        private int find(Object key)
            {
            final K[] ak = this.ak;
            for (int i = 0, c = ak.length; i < c; ++i)
                {
                if (key == ak[i])
                    {
                    return i;
                    }
                }
            for (int i = 0, c = ak.length; i < c; ++i)
                {
                if (key.equals(ak[i]))
                    {
                    return i;
                    }
                }
            return -1;
            }

        private transient Set<K> setKeys;
        private transient Set<Entry<K,V>> setEntries;

        public K[] ak;
        public V[] av;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Holds the index of the key type during deserialization.
     */
    private transient int m_iType;

    /**
     * Holds the indexes of the constant keys during deserialization.
     */
    private transient int[] m_aiKeys;

    /**
     * Holds the indexes of the constant values during deserialization.
     */
    private transient int[] m_aiVals;

    /**
     * The constant format.
     */
    private final Format m_fmt;

    /**
     * The type of the key(s) represented by this constant.
     */
    private TypeConstant m_constType;

    /**
     * The key(s) in the map (or the key in the single map entry).
     */
    private Constant[] m_aconstKey;

    /**
     * The value(s) in the map (or the value in the single map entry).
     */
    private Constant[] m_aconstVal;
    }