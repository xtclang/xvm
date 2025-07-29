package org.xvm.javajit;

import static org.xvm.util.Handy.require;

/**
 * Represents an Ecstasy `Container`.
 */
public class Container {
    /**
     * Construct a new Ecstasy container.
     *
     * @param parent      the parent Container that is creating this Container
     * @param id          the internal id of this Container; -1 is the native container, 0 is the
     *                    main container, and all other values (where n>0) indicate child containers
     *                    created by Ecstasy code
     * @param typeSystem  the TypeSystem for this Container
     * @param injector
     */
    Container(Container parent, long id, TypeSystem typeSystem, Injector injector) {
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
        require("typeSystem", typeSystem);
        require("injector", injector);

        this.xvm        = typeSystem.xvm;
        this.parent     = parent;
        this.id         = id;
        this.typeSystem = typeSystem;
        this.injector   = injector;
    }

    /**
     * The Xvm instance within which this Container exists.
     */
    public final Xvm xvm;

    /**
     * The Container within which this Container was created. The parent Container can only be null
     * iff this is the native Container (id == -1).
     */
    public final Container parent;

    /**
     * The Ecstasy TypeSystem used by this Container.
     */
    public final TypeSystem typeSystem;

    /**
     * The Injector that provides the values for dependency injection into this Container.
     */
    public final Injector injector;

    /**
     * The internal numeric identity of the Container, with -1 being the "native" container, 0 being
     * the "main" container, and >0 being child containers.
     */
    public final long id;

    /**
     * @return true iff the Container is the "core" (or "native") container, which is responsible
     *         for loading the core Ecstasy type system and interfacing with the "native" world
     */
    public boolean isCore() {
        return id == -1;
    }

    /**
     * @return true iff the Container is a "main" container (often referred to as "container zero",
     *         although this implementation allows for more than one main container)
     */
    public boolean isMain() {
        return parent.isCore();
    }

    /**
     * @return true iff the Container is a nested Container, which means that it is neither a "main"
     *         Container nor the "core" Container
     */
    public boolean isNested() {
        return !isCore() && !isMain();
    }

    // TODO create child container
    // TODO control surface area
    // TODO stats surface area

    // ----- memory accounting ---------------------------------------------------------------------

    // TODO
    // public long committed()
    // public long allocated()
}
