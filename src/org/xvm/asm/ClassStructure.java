package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents an entire Class. This is also the base class for module and
 * package structures.
 *
 * @author cp 2016.04.14
 */
public class ClassStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ClassStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module, Package, or Class
     * @param condition  the optional condition for this ClassStructure
     */
    protected ClassStructure(XvmStructure xsParent, int nFlags, IdentityConstant constId,
                             ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Obtain the type parameters for the class as an ordered read-only map, keyed by name and with
     * a corresponding value of the type constraint for the parameter.
     *
     * @return a read-only map of type parameter name to type
     */
    public Map<CharStringConstant, TypeConstant> getTypeParams()
        {
        Map<CharStringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            return Collections.EMPTY_MAP;
            }
        assert (map = Collections.unmodifiableMap(map)) != null;
        return map;
        }

    /**
     * Obtain the type parameters for the class as a list of map entries from name to type.
     *
     * @return a read-only list of map entries from type parameter name to type
     */
    public List<Map.Entry<CharStringConstant, TypeConstant>> getTypeParamsAsList()
        {
        final ListMap<CharStringConstant, TypeConstant> map = m_mapParams;
        if (map == null || map.isEmpty())
            {
            return Collections.EMPTY_LIST;
            }

        List<Map.Entry<CharStringConstant, TypeConstant>> list = map.asList();
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
        }

    /**
     * Add a type parameter.
     *
     * @param sName  the type parameter name
     * @param clz    the type parameter type
     */
    public void addTypeParam(String sName, TypeConstant clz)
        {
        ListMap<CharStringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            m_mapParams = map = new ListMap<>();
            }

        map.put(getConstantPool().ensureCharStringConstant(sName), clz);
        markModified();
        }


    // ----- component methods ---------------------------------------------------------------------

    @Override
    public boolean isClassContainer()
        {
        return true;
        }

    @Override
    public boolean isMethodContainer()
        {
        return true;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        // read in the type parameters
        m_mapParams = disassembleTypeParams(in);

        // read in the "contributions"
        int c = readMagnitude(in);
        if (c > 0)
            {
            final List<Contribution> list = new ArrayList<>();
            final ConstantPool       pool = getConstantPool();
            for (int i = 0; i < c; ++i)
                {
                final Composition      composition = Composition.valueOf(in.readUnsignedByte());
                final ClassConstant    constClass  = (ClassConstant) pool.getConstant(readIndex(in));
                final PropertyConstant constProp   = (PropertyConstant) pool.getConstant(readIndex(in));
                list.add(new Contribution(composition, constClass, constProp));
                }
            m_listContribs = list;
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // register the type parameters
        m_mapParams = registerTypeParams(m_mapParams);

        // register the contributions
        final List<Contribution> listOld = m_listContribs;
        if (listOld != null && listOld.size() > 0)
            {
            final List<Contribution> listNew = new ArrayList<>();
            for (Contribution contribution : listOld)
                {
                listNew.add(new Contribution(
                        contribution.getComposition(),
                        (ClassConstant) pool.register(contribution.getClassConstant()),
                        (PropertyConstant) pool.register(contribution.getDelegatePropertyConstant())));
                }
            m_listContribs = listNew;
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        // write out the type parameters
        assembleTypeParams(m_mapParams, out);

        // write out the contributions
        final List<Contribution> listContribs = m_listContribs;
        final int cContribs = listContribs == null ? 0 : listContribs.size();
        writePackedLong(out, cContribs);
        if (cContribs > 0)
            {
            final ConstantPool pool = getConstantPool();
            for (Contribution contribution : listContribs)
                {
                out.writeByte(contribution.getComposition().ordinal());
                writePackedLong(out, contribution.getClassConstant().getPosition());
                writePackedLong(out, Constant.indexOf(contribution.getDelegatePropertyConstant()));
                }
            }
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", type-params=");

        final ListMap<CharStringConstant, TypeConstant> map = m_mapParams;
        if (map == null || map.size() == 0)
            {
            sb.append("none");
            }
        else
            {
            sb.append('<');
            boolean fFirst = true;
            for (Map.Entry<CharStringConstant, TypeConstant> entry : map.entrySet())
                {
                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    sb.append(", ");
                    }

                sb.append(entry.getKey().getValue());

                TypeConstant constType = entry.getValue();
                if (!constType.isEcstasyObject())
                    {
                    sb.append(" extends ")
                      .append(constType);
                    }
                }
            sb.append('>');
            }

        return sb.toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(toString());

        final List<Contribution> listContribs = m_listContribs;
        final int cContribs = listContribs == null ? 0 : listContribs.size();
        if (cContribs > 0)
            {
            out.print(sIndent);
            out.println("Contributions");
            for (int i = 0; i < cContribs; ++i)
                {
                out.println(sIndent + '[' + i + "]=" + listContribs.get(i));
                }
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ClassStructure) || !super.equals(obj))
            {
            return false;
            }

        ClassStructure that = (ClassStructure) obj;

        // type parameters
        final Map mapThisParams = this.m_mapParams;
        final Map mapThatParams = that.m_mapParams;
        final int cThisParams = mapThisParams == null ? 0 : mapThisParams.size();
        final int cThatParams = mapThatParams == null ? 0 : mapThatParams.size();
        if (cThisParams != cThatParams || (cThisParams > 0 && !mapThisParams.equals(mapThatParams)))
            {
            return  false;
            }

        // contributions (order is considered important)
        final List<Contribution> listThisContribs = this.m_listContribs;
        final List<Contribution> listThatContribs = that.m_listContribs;
        final int cThisContribs = listThisContribs == null ? 0 : listThisContribs.size();
        final int cThatContribs = listThatContribs == null ? 0 : listThatContribs.size();
        if (cThisContribs != cThatContribs || (cThisContribs > 0 && !listThisContribs.equals(listThatContribs)))
            {
            return  false;
            }

        return true;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The name-to-type information for type parameters. The type constant is used to specify a
     * type constraint for the parameter.
     */
    private ListMap<CharStringConstant, TypeConstant> m_mapParams;
    }
