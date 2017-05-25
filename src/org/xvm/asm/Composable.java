package org.xvm.asm;


import java.util.List;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.PropertyConstant;

import org.xvm.util.Handy;


/**
 * TODO
 *
 * @author cp 2017.05.25
 */
public interface Composable
    {
    /**
     * Obtain the class contributions as a list.
     *
     * @return a read-only list of class contributions
     */
    List<Contribution> contributions();

    /**
     * Add a contribution to the composable lazily-instantiated list of contributions.
     *
     * @param contrib  the contribution to add to the end of the list
     */
    void addContribution(Contribution contrib);


    // ----- enumeration: class composition --------------------------------------------------------

    /**
     * Types of composition.
     */
    public enum Composition
        {
        /**
         * An annotation.
         */
        Annotation,
        /**
         * Class inheritance.
         */
        Extends,
        /**
         * Interface inheritance.
         */
        Implements,
        /**
         * Interface inheritance, plus default delegation of interface functionality.
         */
        Delegates,
        /**
         * The combining-in of a trait or mix-in.
         */
        Incorporates,
        /**
         * The class being composed is an enumeration of a specified type.
         */
        Enumerates,;

        /**
         * Look up a Composition enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Composition enum for the specified ordinal
         */
        public static Composition valueOf(int i)
            {
            return COMPOSITIONS[i];
            }

        /**
         * All of the Composition enums.
         */
        private static final Composition[] COMPOSITIONS = Composition.values();
        }

    /**
     * Represents one contribution to the definition of a class. A class (with the term used in the
     * abstract sense, meaning any class, interface, mixin, trait, value, enum, or service) can be
     * composed of any number of contributing components.
     */
    public abstract static class Contribution
            extends XvmStructure
        {
        /**
         * Construct a Contribution.
         *
         * @param xsParent     the containing XVM structure
         * @param composition  specifies the type of composition
         */
        protected Contribution(XvmStructure xsParent, Composition composition)
            {
            super(xsParent);
            m_composition = composition;
            }

        /**
         * Obtain the form of composition represented by this contribution.
         *
         * @return the Composition type for this contribution
         */
        public Composition getComposition()
            {
            return m_composition;
            }

// TODO evaluate
//        @Override
//        public boolean equals(Object obj)
//            {
//            if (this == obj)
//                {
//                return true;
//                }
//
//            if (!(obj instanceof Contribution))
//                {
//                return false;
//                }
//
//            Contribution that = (Contribution) obj;
//            return this.m_composition == that.m_composition
//                    && this.m_constClass.equals(that.m_constClass)
//                    && Handy.equals(this.m_constProp, that.m_constProp);
//            }
//
//        @Override
//        public String toString()
//            {
//            String s = m_composition.toString().toLowerCase() + ' ' + m_constClass.getDescription();
//            return m_constProp == null ? s : s + '(' + m_constProp.getDescription() + ')';
//            }

        /**
         * Defines the form of composition that this component contributes to the class.
         */
        private Composition m_composition;
        }
    }
