package org.xvm.asm.constants;


import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.TypedefStructure;

import org.xvm.util.Hash;


/**
 * Represents a child class or typedef contained within a class.
 */
public class ChildInfo {
    // ----- constructors --------------------------------------------------------------------------
    /**
     * Construct a ChildInfo.
     *
     * @param child  the child component
     */
    public ChildInfo(Component child) {
        this(child, child.getAccess(), Collections.singleton(child.getIdentityConstant()));
        assert child instanceof ClassStructure || child instanceof TypedefStructure;
    }

    /**
     * Construct a ChildInfo.
     *
     * @param child   the child component
     * @param access  the visibility of the child
     * @param setIds  the ids that the child is known by
     */
    protected ChildInfo(
            Component             child,
            Access                access,
            Set<IdentityConstant> setIds) {
        this(null, child, access, setIds);
    }

    /**
     * Internal: Construct a ChildInfo owned by the specified TypeInfo.
     */
    private ChildInfo(
            TypeInfo              infoType,
            Component             child,
            Access                access,
            Set<IdentityConstant> setIds) {
        m_infoType = infoType;
        f_child    = child;
        f_access   = access;
        f_setIds   = setIds;
    }

    /**
     * Internal: Create a ChildInfo for the specified TypeInfo. Do not mutate an unowned source
     * ChildInfo here; TypeInfoReal calls this while it is still under construction.
     */
    final synchronized ChildInfo forType(TypeInfo infoType) {
        assert infoType != null;

        return m_infoType == infoType
                ? this
                : new ChildInfo(infoType, f_child, f_access, new HashSet<>(f_setIds));
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the name of the child
     */
    public String getName() {
        return f_child.getName();
    }

    /**
     * @return the containing TypeInfo, or null while this ChildInfo is being assembled
     */
    public TypeInfo getTypeInfo() {
        return m_infoType;
    }

    /**
     * @return the visibility of the child
     */
    public Access getAccess() {
        return f_access;
    }

    /**
     * @return the primary identity of the child
     */
    public IdentityConstant getIdentity() {
        return f_child.getIdentityConstant();
    }

    /**
     * @return a set of known identities of the child
     */
    public Set<IdentityConstant> getAllIdentities() {
        return Collections.unmodifiableSet(f_setIds);
    }

    /**
     * @return the underlying child structure
     */
    public Component getComponent() {
        return f_child;
    }

    /**
     * @return true iff the child info represents a virtual child that is not an interface
     */
    public boolean isVirtualClass() {
        return f_child instanceof ClassStructure &&
                ((ClassStructure) f_child).isVirtualChildClass();
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Determine if this ChildInfo and another ChildInfo are referring to the same component.
     *
     * @param that  another ChildInfo by the same name
     *
     * @return the resulting ChildInfo to use, or null if a name collision occurs that requires the
     *         name to be unavailable (e.g. because the ChildInfo objects are not referring to the
     *         same component)
     */
    protected ChildInfo layerOn(ChildInfo that) {
        if (this.f_setIds.equals(that.f_setIds) || this.f_setIds.containsAll(that.f_setIds)) {
            return this;
        }

        if (that.f_setIds.containsAll(this.f_setIds)) {
            return that;
        }

        boolean fCombine = false;
        if (this.f_setIds.stream().anyMatch(id -> that.f_setIds.contains(id))) {
            fCombine = true;
        } else if (this.f_child instanceof ClassStructure clzThis &&
                 that.f_child instanceof ClassStructure clzThat) {
            // "static" attribute must be the same
            if (clzThis.isStatic() == clzThat.isStatic()) {
                if (clzThat.isStatic()) {
                    // a static child class fully "covers" the base one
                    return that;
                }

                // for virtual child assume that override is all that we need;
                // if the combination is illegal, that needs to be detected by the TypeInfo creation
                // for the child itself
                fCombine = clzThat.isExplicitlyOverride();
            }
        }

        if (fCombine) {
            // there's identity overlap, so the referred-to child is the same
            Component child  = that.f_child;
            Access    access = this.getAccess().maxOf(that.getAccess());
            Set<IdentityConstant> setIds = new HashSet<>(this.f_setIds);
            setIds.addAll(that.f_setIds);
            return new ChildInfo(child, access, setIds);
        } else {
            return null;
        }
    }


    // ----- Object methods ------------------------------------------------------------------------


    @Override
    public boolean equals(Object obj) {
        return obj instanceof ChildInfo that
            && this.f_child.equals(that.f_child)
            && Objects.equals(this.f_access, that.f_access)
            && Objects.equals(this.f_setIds, that.f_setIds)
            ;
    }

    @Override
    public int hashCode() {
        return Hash.of(f_setIds, Hash.of(f_access, f_child.hashCode()));
    }

    @Override
    public String toString() {
        return "ChildInfo: " + getAccess() + ' ' + getName();
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The child component.
     */
    private final Component f_child;

    /**
     * The accessibility that this child is declared with.
     */
    private final Access f_access;

    /**
     * The potential identities that this child is known as.
     */
    private final Set<IdentityConstant> f_setIds;

    /**
     * The TypeInfo that contains this ChildInfo, or null while it is being assembled.
     */
    private final TypeInfo m_infoType;
}
