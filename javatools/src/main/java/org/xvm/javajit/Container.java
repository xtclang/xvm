package org.xvm.javajit;


import java.util.Iterator;

import static org.xvm.util.Handy.require;


/**
 * Represents an Ecstasy `Container`.
 */
class Container {
    /**
     * Construct a new Ecstasy container.
     *
     * @param parent      the parent Container that is creating this Container
     * @param id          the internal id of this Container; -1 is the native container, 0 is the
     *                    main container, and all other values (where n>0) indicate child containers
     *                    created by Ecstasy code
     * @param typeSystem
     */
    Container(Container parent, int id, TypeSystem typeSystem) {
        if (id < 0) {
            if (id == -1) {
                if (parent != null) {
                    throw new IllegalArgumentException("the native container (-1) can not have a parent");
                }
            } else {
                throw new IllegalArgumentException("illegal id: " + id);
            }
        } else {
            require("parent", parent);
        }

        this.xvm        = typeSystem.xvm;
        this.parent     = parent;
        this.id         = id;
        this.typeSystem = typeSystem;
    }

    /**
     * The Xvm instance within which this Container exists.
     */
    final Xvm xvm;

    /**
     * The Container within which this Container was created. The parent Container can only be null
     * iff this is the native Container (id == -1).
     */
    final Container parent;

    /**
     * The Ecstasy TypeSystem used by this Container.
     */
    final TypeSystem typeSystem;

    /**
     * The internal numeric identity of the Container, with -1 being the "native" container, 0 being
     * the "main" container, and >0 being child containers.
     */
    final int id;

    /**
     * @return the internal numeric identity of the Container, with -1 being the "native" container
     *         and 0 being the "main" container
     */
    public Xvm getXvm() {
        return xvm;
    }

    /**
     * @return the parent Container, or null iff this is the "native" (aka bootstrap) Container
     */
    public Container getParent() {
        return parent;
    }

    /**
     * @return the internal numeric identity of the Container, with -1 being the "native" container
     *         and 0 being the "main" container
     */
    public int getId() {
        return id;
    }

    /**
     * @return the TypeSystem of the Container
     */
    public TypeSystem getTypeSystem() {
        return typeSystem;
    }

    // TODO create child container
    // TODO control surface area
    // TODO stats surface area
}
