package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a constant value that contains a number of other constant values. Specifically this
 * supports the array, tuple, and set types.
 */
public class ArrayConstant
        extends ValueConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is an array, tuple, or set.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param fmt        the format of the constant
     * @param constType  the data type of the constant
     */
    public ArrayConstant(ConstantPool pool, Format fmt, TypeConstant constType) {
        this(pool, fmt, constType, List.of());
    }

    /**
     * Construct a constant whose value is an array or tuple.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param fmt        the format of the constant
     * @param constType  the data type of the constant
     * @param values     the values of the constant (never null, use empty list)
     */
    public ArrayConstant(ConstantPool pool, Format fmt, TypeConstant constType,
                         @NotNull List<Constant> values) {
        super(pool);
        validateFormatAndType(fmt, constType);

        f_fmt       = fmt;
        m_constType = constType;
        m_listVal   = List.copyOf(Objects.requireNonNull(values, "values required"));
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
    public ArrayConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);

        int iType = readMagnitude(in);
        int cVals = readMagnitude(in);
        int[] aiVal = new int[cVals];
        for (int i = 0; i < cVals; ++i) {
            aiVal[i] = readMagnitude(in);
        }

        f_fmt   = format;
        m_iType = iType;
        m_aiVal = aiVal;
    }

    @Override
    protected void resolveConstants() {
        ConstantPool pool = getConstantPool();

        m_constType = (TypeConstant) pool.getConstant(m_iType);

        int[]          aiConst = m_aiVal;
        int            cConsts = aiConst.length;
        List<Constant> list    = new ArrayList<>(cConsts);
        for (int i = 0; i < cConsts; ++i) {
            list.add(pool.getConstant(aiConst[i]));
        }
        m_listVal = List.copyOf(list);
    }

    private void validateFormatAndType(Format fmt, TypeConstant constType) {
        if (fmt == null) {
            throw new IllegalArgumentException("format required");
        }
        if (constType == null) {
            throw new IllegalArgumentException("type required");
        }

        // determine what the class must be based on the constant format
        String sClassName = switch (fmt) {
            case Array, Tuple, Set -> fmt.name();
            default -> throw new IllegalArgumentException("unsupported format: " + fmt);
        };

        // require that the underlying identity be a class (not an auto-narrowing type for example)
        // and make sure that it matches
        if (!constType.isEcstasy(sClassName)) {
            throw new IllegalArgumentException("type for " + fmt + " must be " + sClassName
                    + " (unsupported type: " + constType + ")");
        }
    }

    @Override
    public Constant convertTo(TypeConstant typeOut) {
        if (typeOut.isSingleDefiningConstant()) {
            ConstantPool pool = getConstantPool();

            if (typeOut.getDefiningConstant().equals(pool.clzArray())) {
                TypeConstant typeEl = typeOut.getParamType(0);

                List<Constant> listIn  = getValues();
                int            cValues = listIn.size();
                List<Constant> listOut = new ArrayList<>(cValues);

                for (Constant constIn : listIn) {
                    Constant constEl = constIn.convertTo(typeEl);
                    if (constEl == null) {
                        return null;
                    }
                    listOut.add(constEl);
                }
                return pool.ensureArrayConstant(typeOut, listOut);
            }
        }
        return null;
    }


    // ----- ValueConstant methods -----------------------------------------------------------------

    @Override
    public TypeConstant getType() {
        return m_constType;
    }

    /**
     * {@inheritDoc}
     * @return  the constant's contents as an immutable list of constants
     */
    @Override
    public List<Constant> getValue() {
        return m_listVal;
    }

    /**
     * @return the constant's contents as an immutable list of constants
     */
    public List<Constant> getValues() {
        return m_listVal;
    }

    /**
     * Get the values as an array for backward compatibility.
     *
     * @return the constant's contents as an array (creates a new array each call)
     * @deprecated Use {@link #getValues()} instead for immutability.
     */
    @Deprecated
    public Constant[] getValueArray() {
        return m_listVal.toArray(Constant[]::new);
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return f_fmt;
    }

    @Override
    public boolean isValueCacheable() {
        return !m_constType.containsFormalType(true);
    }

    @Override
    public boolean containsUnresolved() {
        if (isHashCached()) {
            return false;
        }

        if (m_constType.containsUnresolved()) {
            return true;
        }

        for (Constant constant : m_listVal) {
            if (constant.containsUnresolved()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor) {
        visitor.accept(m_constType);
        for (Constant constant : m_listVal) {
            visitor.accept(constant);
        }
    }

    @Override
    public ArrayConstant resolveTypedefs() {
        TypeConstant typeOld = m_constType;
        TypeConstant typeNew = typeOld.resolveTypedefs();

        // check values
        List<Constant> listOld     = m_listVal;
        List<Constant> listNew     = null;
        boolean        fAnyChanged = false;

        for (int i = 0, c = listOld.size(); i < c; ++i) {
            Constant constOld = listOld.get(i);
            Constant constNew = constOld.resolveTypedefs();
            if (constNew != constOld) {
                if (!fAnyChanged) {
                    listNew     = new ArrayList<>(listOld);
                    fAnyChanged = true;
                }
                listNew.set(i, constNew);
            }
        }

        return typeNew == typeOld && !fAnyChanged
                ? this
                : (ArrayConstant) getConstantPool().register(
                        new ArrayConstant(getConstantPool(), f_fmt, typeNew,
                                fAnyChanged ? listNew : listOld));
    }

    @Override
    protected int compareDetails(Constant that) {
        if (!(that instanceof ArrayConstant thatArray)) {
            return -1;
        }
        int nResult = this.m_constType.compareTo(thatArray.m_constType);
        if (nResult != 0) {
            return nResult;
        }

        List<Constant> listThis = this.m_listVal;
        List<Constant> listThat = thatArray.m_listVal;
        int cThis = listThis.size();
        int cThat = listThat.size();
        for (int i = 0, c = Math.min(cThis, cThat); i < c; ++i) {
            nResult = listThis.get(i).compareTo(listThat.get(i));
            if (nResult != 0) {
                return nResult;
            }
        }
        return cThis - cThat;
    }

    @Override
    public String getValueString() {
        List<Constant> list    = m_listVal;
        int            cConsts = list.size();

        String sStart;
        String sEnd;
        switch (f_fmt) {
        case Array:
            sStart = "[";
            sEnd   = "]";
            break;
        case Tuple:
            sStart = cConsts < 2 ? "Tuple:(" : "(";
            sEnd   = ")";
            break;
        case Set:
            sStart = "Set:{";
            sEnd   = "}";
            break;
        default:
            throw new IllegalArgumentException("illegal format: " + f_fmt);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(sStart);

        for (int i = 0; i < cConsts; ++i) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(list.get(i));
        }

        sb.append(sEnd);
        return sb.toString();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        m_constType = pool.register(m_constType);

        List<Constant> listOld = m_listVal;
        List<Constant> listNew = new ArrayList<>(listOld.size());
        for (Constant constant : listOld) {
            listNew.add(pool.register(constant));
        }
        m_listVal = List.copyOf(listNew);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constType.getPosition());
        List<Constant> list = m_listVal;
        writePackedLong(out, list.size());
        for (Constant constant : list) {
            writePackedLong(out, constant.getPosition());
        }
    }

    @Override
    public String getDescription() {
        return "array-length=" + m_listVal.size();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_constType,
               Hash.of(m_listVal));
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Holds the indexes of the type during deserialization.
     */
    private transient int m_iType;

    /**
     * Holds the indexes of the constant values during deserialization.
     */
    private transient int[] m_aiVal;

    /**
     * The constant format.
     */
    private final Format f_fmt;

    /**
     * The type represented by this constant. Note that this is not the element type, but rather is
     * the type of the array, tuple, or set.
     */
    private TypeConstant m_constType;

    /**
     * The values in the array, tuple, or set (immutable list).
     */
    private List<Constant> m_listVal;
}


