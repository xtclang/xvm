import annotations.DisplayName;

/**
 * A class that can generate display names for classes and methods.
 */
interface DisplayNameGenerator
        extends Const {
    /**
     * Generate a non-empty, non-blank display name for the specified class.
     *
     * @param clz  the class to generate a name for
     *
     * @return the display name for the class
     */
    String nameForClass(Class clz);

    /**
     * Generate a non-empty, non-blank display name for the specified inner class.
     *
     * @param clz  the class to generate a name for
     *
     * @return the display name for the class
     */
    String nameForNestedClass(Class clz);

    /**
     * Generate a non-empty, non-blank display name for the specified method.
     *
     * @param clz     the class the test method will be (or was) invoked on
     * @param method  the test method to generate a display name for
     *
     * @return the display name for the method
     */
    String nameForMethod(Class clz, MethodOrFunction method);

    /**
     * Generate a non-empty, non-blank string representation for the parameters of the
     * specified method.
     *
     * @param method  the method to extract the parameter types from
     *
     * @return a string representation of all parameter types of the method
     */
    static String parameterTypesAsString(MethodOrFunction method) {
        StringBuffer buf = new StringBuffer().add('(');

        EachParam: for (Parameter param : method.params) {
            if (!EachParam.first) {
                ", ".appendTo(buf);
            }
            param.appendTo(buf);
        }

        return buf.add(')').toString();
    }

    /**
     * A singleton instance of the default `DisplayNameGenerator`.
     */
    static DisplayNameGenerator Default = new DefaultDisplayNameGenerator();

    /**
     * The default `DisplayNameGenerator` implementation.
     */
    static const DefaultDisplayNameGenerator
            implements DisplayNameGenerator {
        @Override
        String nameForClass(Class clz) {
            if (clz.is(DisplayName)) {
                return clz.name;
            }
            return clz.name;
        }

        @Override
        String nameForNestedClass(Class clz) {
            if (clz.is(DisplayName)) {
                return clz.name;
            }
            return clz.name;
        }

        @Override
        String nameForMethod(Class clz, MethodOrFunction method) {
            if (method.is(DisplayName)) {
                return method.name;
            }
            return clz.name + "." + method.name + parameterTypesAsString(method);
        }
    }
}
