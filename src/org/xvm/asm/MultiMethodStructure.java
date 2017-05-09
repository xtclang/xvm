package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a multi-method, which is a group of methods that share a name.
 * The multi-method does not have a corresponding development-time analogy; a developer does NOT
 * declare or define a multi-method. Instead, it is a compile-time construction, used to collect
 * together methods that share a name into a group, within which they are identified by a more
 * exacting set of attributes, namely their accessibility and their parameter/return types.
 *
 * @author cp 2016.04.26
 */
public class MultiMethodStructure
        extends StructureContainer
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MultiMethodStructure with the specified identity.
     *
     * @param structParent  the XvmStructure (a ModuleStructure, a PackageStructure, a
     *                      ClassStructure, a PropertyStructure, or a MethodStructure) that
     *                      contains this MultiMethodStructure
     * @param constMulti    the constant that specifies the identity of the property
     */
    public MultiMethodStructure(XvmStructure structParent, MultiMethodConstant constMulti)
        {
        super(structParent);

        this.constMulti = constMulti;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the name of the multi-method
     */
    public String getName()
        {
        return constMulti.getName();
        }

    /**
     * @return a collection of methods contained within this MultiMethodStructure; the caller must
     *         treat the collection as a read-only object
     */
    public Collection<MethodStructure> methods()
        {
        Collection coll = methods.values();
        assert (coll = Collections.unmodifiableCollection(coll)) != null;
        return coll;
        }

    /**
     * TODO
     *
     * @param fFunction
     * @param access
     * @param returnTypes
     * @param paramTypes
     * @return
     */
    public MethodStructure createMethod(boolean fFunction, Access access, TypeConstant[] returnTypes, TypeConstant[] paramTypes)
        {
        MethodConstant constant = getConstantPool().ensureMethodConstant(getIdentityConstant(), getName(), access, returnTypes, paramTypes);
        MethodStructure method = new MethodStructure(this, constant);
        methods.put(constant, method);
        return method;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public MultiMethodConstant getIdentityConstant()
        {
        return constMulti;
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        return methods.values().iterator();
        }

    @Override
    protected void disassemble(DataInput in)
    throws IOException
        {
        constMulti = (MultiMethodConstant) getConstantPool().getConstant(readIndex(in));

        List<MethodStructure> list = (List<MethodStructure>) disassembleSubStructureCollection(in);
        methods.clear();
        for (MethodStructure struct : list)
            {
            methods.put(struct.getMethodConstant(), struct);
            }
        assert methods.size() == list.size();

        super.disassemble(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        ((Constant) constMulti).registerConstants(pool);
        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
    throws IOException
        {
        writePackedLong(out, constMulti.getPosition());
        assembleSubStructureCollection(methods.values(), out);
        super.assemble(out);
        }

    @Override
    public String getDescription()
        {
        return new StringBuilder()
                .append("name=")
                .append(constMulti.getName())
                .append(", method-count=")
                .append(methods.size())
                .append(", ")
                .append(super.getDescription())
                .toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(toString());

        dumpStructureMap(out, sIndent, "Methods", methods);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof MultiMethodStructure))
            {
            return false;
            }

        MultiMethodStructure that = (MultiMethodStructure) obj;
        return this.constMulti.equals(that.constMulti)
                && equalMaps(this.methods, that.methods);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The identity constant for the MultiMethodStructure.
     */
    private MultiMethodConstant constMulti;

    /**
     * The methods contained within the multi-method, keyed by method constant.
     */
    private Map<MethodConstant, MethodStructure> methods = new HashMap<>(3);
    }
