package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.xvm.asm.ConstantPool.ModuleConstant;
import org.xvm.asm.ConstantPool.VersionConstant;
import org.xvm.asm.StructureContainer.PackageContainer;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents an entire Module.
 *
 * @author cp 2016.04.14
 */
public class ModuleStructure
        extends PackageContainer
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct a ModuleStructure with the specified identity.
     *
     * @param structParent  the XvmStructure (probably a FileStructure) that
     *                      contains this ModuleStructure
     * @param constmodule   the constant that specifies the identity of the
     *                      Module
     */
    ModuleStructure(XvmStructure structParent, ModuleConstant constmodule)
        {
        super(structParent, constmodule);
        }


    // ----- XvmStructure methods ----------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final int cVers = readMagnitude(in);
        if (cVers > 0)
            {
            final SortedSet<VersionConstant> setVer = m_setVer;
            final ConstantPool               pool   = getConstantPool();
            for (int i = 0; i < cVers; ++i)
                {
                setVer.add((VersionConstant) pool.getConstant(readMagnitude(in)));
                }
            }

        super.disassemble(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        final TreeSet<VersionConstant> setOld = m_setVer;
        if (!setOld.isEmpty())
            {
            final TreeSet<VersionConstant> setNew = new TreeSet<>();

            for (VersionConstant ver : setOld)
                {
                setNew.add((VersionConstant) pool.register(ver));
                }

            m_setVer = setNew;
            }

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        final TreeSet<VersionConstant> setVer = m_setVer;
        writePackedLong(out, setVer.size());
        for (VersionConstant ver : setVer)
            {
            writePackedLong(out, ver.getPosition());
            }

        super.assemble(out);
        }

    @Override
    public String getDescription()
        {
        final TreeSet<VersionConstant> setVer = m_setVer;

        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", version=");

        switch (setVer.size())
            {
            case 0:
                sb.append("none");
                break;
            case 1:
                sb.append(setVer.iterator().next().getVersionString());
                break;
            default:
                sb.append("multiple");
                break;
            }

        return sb.toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        super.dump(out, sIndent);
        dumpStructureCollection(out, sIndent, "Versions", m_setVer);
        }


    // ----- Object methods ----------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ModuleStructure && super.equals(obj)))
            {
            return false;
            }

        // compare versions
        ModuleStructure that = (ModuleStructure) obj;
        return this.m_setVer.equals(that.m_setVer);
        }


    // ----- accessors ---------------------------------------------------------

    /**
     * Obtain the ModuleConstant that holds the identity of this Module.
     *
     * @return the ModuleConstant representing the identity of this
     *         ModuleStructure
     */
    public ModuleConstant getModuleConstant()
        {
        return (ModuleConstant) getIdentityConstant();
        }

    /**
     * If this ModuleStructure contains an unlabeled version of a module, then
     * label the module in this module structure with the provided version
     * number; if this ModuleStructure contains a single version label, then
     * replace that version label with the specified version number.
     *
     * @param ver  the version number for the module in this module structure
     *
     * @throws IllegalStateException if this module structure has more than one
     *         version label
     */
    public void labelModuleVersion(VersionConstant ver)
        {
        SortedSet<VersionConstant> setVer = m_setVer;
        if (setVer.size() <= 1)
            {
            setVer.clear();
            setVer.add(ver);
            markModified();
            }
        else
            {
            throw new IllegalStateException("the module (" + getModuleConstant()
                    + ") contains more than one version label");
            }
        }

    /**
     * Given a second ModuleStructure containing one or more version labels
     * of the same module as is found in this module structure, merge those
     * module versions into this module. Note that any version labels in
     * the second module structure that exist in this module structure will
     * not be merged, as they already exist in this module structure.
     *
     * @param that  a module structure containing one or more version labels
     *              of the same module that is stored in this module structure
     *
     * @throws IllegalStateException if this module structure does not contain
     *         version label(s)
     * @throws IllegalArgumentException if either that module structure does
     *         not contain a module with the same identity, or it does not
     *         contain version label(s)
     */
    public void mergeVersions(ModuleStructure that)
        {
        if (!this.isVersioned())
            {
            throw new IllegalStateException("first module " + this.getModuleConstant()
                    + " does not contain a version label");
            }

        if (that == null)
            {
            throw new IllegalArgumentException("second module is required");
            }

        if (!this.getModuleConstant().equals(that.getModuleConstant()))
            {
            throw new IllegalArgumentException("second module (" + that.getModuleConstant()
                    + ") does not match the first module (" + this.getModuleConstant() + ")");
            }

        if (!that.isVersioned())
            {
            throw new IllegalArgumentException("second module " + that.getModuleConstant()
                    + " does not contain a version label");
            }


        SortedSet<VersionConstant> setThisVer = this.m_setVer;
        SortedSet<VersionConstant> setThatVer = that.m_setVer;
        for (VersionConstant ver : setThatVer)
            {
            if (!setThisVer.contains(ver))
                {
                // TODO - actual merge processing
                setThisVer.add(ver);
                markModified();
                }
            }
        }

    /**
     * Remove a version label of the module from this module structure. If
     * there are multiple versions within this module structure, then only
     * the specified version label is removed. If there is only one version
     * within this module structure, then the structure is left unchanged,
     * but the version label is removed.
     *
     * @param ver  the version label to remove from this module structure
     *
     * @throws IllegalArgumentException if the specified version label does
     *         not exist within this module structure
     */
    public void purgeVersion(VersionConstant ver)
        {
        if (ver == null)
            {
            throw new IllegalArgumentException("version required");
            }

        SortedSet<VersionConstant> setVer = m_setVer;
        if (!setVer.contains(ver))
            {
            throw new IllegalArgumentException("version (" + ver  + ") does not exist in this module");
            }

        if (setVer.size() > 1)
            {
            // TODO - actual remove processing
            }

        setVer.remove(ver);
        markModified();
        }

    /**
     * Determine if the module structure contains version information.
     *
     * @return true if this module structure contains one or more versions of
     *         the module
     */
    boolean isVersioned()
        {
        return !m_setVer.isEmpty();
        }

    /**
     * Determine if the specified version of the module is contained within
     * this module structure.
     *
     * @param ver  a version number
     *
     * @return true if this module structure contains the specified version
     */
    public boolean containsVersion(VersionConstant ver)
        {
        return m_setVer.contains(ver);
        }

    /**
     * Determine if the specified version of the module is supported by a
     * version label that is within this module structure.
     *
     * @param ver     a version number
     * @param fExact  true if the version has to match exactly
     *
     * @return true if this module structure supports the specified version
     */
    public boolean supportsVersion(VersionConstant ver, boolean fExact)
        {
        if (m_setVer.contains(ver))
            {
            return true;
            }

        if (!fExact)
            {
            // TODO is there a version that supports the specified version?
            }

        return false;
        }

    /**
     * Obtain a set of all of the versions of the module contained within this
     * module structure.
     *
     * @return a SortedSet of the versions contained within this module
     *         structure; if the set is empty, that indicates that the module
     *         structure does not contain version information (the module
     *         information in the module structure is not version labeled)
     */
    public SortedSet<VersionConstant> getVersions()
        {
        return Collections.unmodifiableSortedSet(m_setVer);
        }


    // ----- data members ------------------------------------------------------

    /**
     * Set of versions held by this module.
     * <ul>
     * <li>If the set is empty, that indicates that the module structure does
     * not contain version information (the module information in the module
     * structure is not version labeled.)</li>
     * <li>If the set contains one version, that indicates that the module
     * structure contains a single version label, i.e. there is a single
     * version of the module inside of the module structure.</li>
     * <li>If the set contains more than one version, that indicates that the
     * module structure contains multiple different versions of the module,
     * and must be resolved in order to link the module.</li>
     * </ul>
     */
    private TreeSet<VersionConstant> m_setVer = new TreeSet<>();
    }
