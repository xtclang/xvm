package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.util.LinkedIterator;


/**
 * An XVM structure that can contain PackageStructure objects, in addition to ClassStructure,
 * PropertyStructure, and MethodStructure objects.
 */
public abstract class PackageContainer
        extends ClassContainer
    {
    // ----- constructors ----------------------------------------------

    /**
     * Construct a PackageContainer.
     *
     * @param xsParent  the containing XVM structure
     * @param constId   the identity constant for this XVM structure
     */
    protected PackageContainer(XvmStructure xsParent, Constant constId)
        {
        super(xsParent, constId);
        }

    // ----- XvmStructure methods --------------------------------------

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        final Map map = m_mapPackage;
        final Iterator iter = super.getContained();
        return map == null
                ? iter
                : new LinkedIterator<>(iter, map.values().iterator());
        }

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        List<PackageStructure> listStruct = (List<PackageStructure>) disassembleSubStructureCollection(in);
        if (listStruct.isEmpty())
            {
            m_mapPackage = null;
            }
        else
            {
            Map<String, PackageStructure> mapPackage = ensurePackageMap();
            mapPackage.clear();
            for (PackageStructure struct : listStruct)
                {
                mapPackage.put(struct.getPackageConstant().getName(), struct);
                }
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);
        assembleSubStructureCollection(getPackageMap().values(), out);
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        super.dump(out, sIndent);
        dumpStructureMap(out, sIndent, "Packages", m_mapPackage);
        }

    // ----- Object methods --------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof org.xvm.asm.PackageContainer && super.equals(obj)))
            {
            return false;
            }

        // compare packages
        org.xvm.asm.PackageContainer that = (org.xvm.asm.PackageContainer) obj;
        return equalMaps(this.m_mapPackage, that.m_mapPackage);
        }

    // ----- PackageStructure children ---------------------------------

    /**
     * Get an iterator over the package names that are contained immediately
     * within this structure.
     *
     * @return an Iterator of package names
     */
    public Iterator<String> packageNames()
        {
        final Map<String, PackageStructure> mapPackage = m_mapPackage;
        return mapPackage == null
                ? Collections.emptyIterator()
                : mapPackage.keySet().iterator();
        }

    /**
     * Find the package with the specified name.
     * <p>
     * This method allows a composite (dot-delimited) package name to be
     * specified.
     *
     * @param sName  the name of the package
     *
     * @return the PackageStructure for the specified package, or null if it
     *         does not exist
     */
    public PackageStructure getPackage(String sName)
        {
        // handle the case in which the name is a dot-delimited package name
        String sRemainder = null;
        int ofDot = sName.indexOf('.');
        if (ofDot >= 0)
            {
            sRemainder = sName.substring(ofDot + 1);
            sName = sName.substring(0, ofDot);
            }

        // see if the package already exists
        PackageStructure structpackage = lookupPackage(sName);
        return structpackage == null || sRemainder == null
                ? structpackage
                : structpackage.getPackage(sRemainder);
        }

    /**
     * Find the package with the specified name, creating it if necessary.
     * <p>
     * This method allows a composite (dot-delimited) package name to be
     * specified.
     *
     * @param sName  the name of the package
     *
     * @return the PackageStructure for the specified package
     */
    public PackageStructure ensurePackage(String sName)
        {
        // handle the case in which the name is a dot-delimited package name
        String sRemainder = null;
        int ofDot = sName.indexOf('.');
        if (ofDot >= 0)
            {
            sRemainder = sName.substring(ofDot + 1);
            sName = sName.substring(0, ofDot);
            }

        // see if the package already exists
        PackageStructure structpackage = lookupPackage(sName);
        if (structpackage == null)
            {
            // create & register the package
            structpackage = createPackage(sName);
            }

        return sRemainder == null ? structpackage :
                structpackage.ensurePackage(sRemainder);
        }

    /**
     * Remove the specified package from this structure.
     * <p>
     * This method allows a composite (dot-delimited) package name to be
     * specified.
     *
     * @param sName  the name of the package
     */
    public void deletePackage(String sName)
        {
        final Map<String, PackageStructure> mapPackage = m_mapPackage;
        if (mapPackage != null)
            {
            // handle the case in which the name is a dot-delimited package name
            final int ofDot = sName.indexOf('.');
            if (ofDot >= 0)
                {
                PackageStructure structpackage = lookupPackage(sName.substring(0, ofDot));
                if (structpackage != null)
                    {
                    structpackage.deletePackage(sName.substring(ofDot + 1));
                    }
                }
            else
                {
                mapPackage.remove(sName);
                }
            }
        }

    /**
     * Obtain a read-only map from String name to PackageStructure.
     *
     * @return a non-null Map containing the various PackageStructure
     *         objects keyed by their non-qualified (simple) names
     */
    protected Map<String, PackageStructure> getPackageMap()
        {
        final Map<String, PackageStructure> mapPackage = m_mapPackage;
        return mapPackage == null ? Collections.EMPTY_MAP : mapPackage;
        }

    /**
     * Obtain a mutable map from String name to PackageStructure.
     *
     * @return a non-null Map containing the various PackageStructure
     *         objects keyed by their non-qualified (simple) names
     */
    protected Map<String, PackageStructure> ensurePackageMap()
        {
        Map<String, PackageStructure> mapPackage = m_mapPackage;
        if (mapPackage == null)
            {
            m_mapPackage = mapPackage = new HashMap<>();
            }
        return mapPackage;
        }

    /**
     * Find the package with the specified name.
     *
     * @param sName  the simple (unqualified) package name to find
     *
     * @return the PackageStructure for the specified package, or null if no
     *         package with that name is contained immediately within this
     *         structure
     */
    protected PackageStructure lookupPackage(String sName)
        {
        final Map<String, PackageStructure> mapPackage = m_mapPackage;
        return mapPackage == null ? null : mapPackage.get(sName);
        }

    /**
     * Create and register a PackageStructure with the specified package name.
     *
     * @param sName  the simple (unqualified) package name to create
     */
    protected PackageStructure createPackage(String sName)
        {
        assert sName != null;

        Map<String, PackageStructure> mapPackage = ensurePackageMap();
        assert !mapPackage.containsKey(sName);

        final ConstantPool     pool         = getConstantPool();
        final Constant         constthis    = getIdentityConstant();
        final PackageConstant constpackage = pool.ensurePackageConstant(constthis, sName);
        final PackageStructure structpackage = new PackageStructure(this, constpackage);

        mapPackage.put(sName, structpackage);
        return structpackage;
        }

    // ----- data members ----------------------------------------------

    /**
     * A lazily instantiated name-to-package lookup table.
     */
    private Map<String, PackageStructure> m_mapPackage;
    }
