package org.xvm.asm;


import org.xvm.compiler.ast.AstNode;


/**
 * ComponentResolver represents the name resolution functionality of the Component.
 */
public interface ComponentResolver
    {
    /**
     * Determine what the specified name is referring to.
     *
     * @param sName      the name to resolve
     * @param access     the accessibility to the component this resolver represents
     * @param collector  the collector to which the potential name matches will be reported
     *
     * @return the resolution result
     */
    ResolutionResult resolveName(String sName, Constants.Access access, ResolutionCollector collector);

    enum ResolutionResult
        {
        UNKNOWN, RESOLVED, POSSIBLE, ERROR;

        /**
         * Combine this result with the specified one to produce better information.
         *
         * @param that  another result
         *
         * @return a combined result
         */
        public ResolutionResult combine(ResolutionResult that)
            {
            return switch (this)
                {
                case POSSIBLE, ERROR -> this;
                default              -> that;
                };
            }
        }
    /**
     * A callback interface used by the name resolution functionality of the Component.
     */
    interface ResolutionCollector
        {
        /**
         * Invoked when a name resolves to a child component.
         *
         * @param component  the child component (which may be a composite)
         */
        ResolutionResult resolvedComponent(Component component);

        /**
         * Invoked when a name resolves to something that is a constant, such as a property
         * constant of a parameterized type or of a method.
         *
         * @param constant  either a PropertyConstant or a TypeParameterConstant
         */
        ResolutionResult resolvedConstant(Constant constant);

        /**
         * Provide an AstNode to report resolution issues for.
         */
        default AstNode getNode()
            {
            return null;
            }

        /**
         * Provide an ErrorListener to report resolution issues to.
         */
        default ErrorListener getErrorListener()
            {
            return ErrorListener.BLACKHOLE;
            }
        }
    }