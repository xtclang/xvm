/**
 * A Vector represents a one-dimensional container of values.
 *
 * TODO
 * * mixins for e.g. numeric types (from Array)
 */
@Abstract class Vector<Element>
        implements List<Element>
        incorporates conditional HashableVector<Element extends Hashable>
        incorporates conditional FreezableVector<Element extends Shareable> {

    // ----- Hashable mixin ------------------------------------------------------------------------

    private static mixin HashableVector<Element extends Hashable>
            into Vector<Element>
            implements Hashable {
        /**
         * Calculate a hash code for a given Vector. The resulting hash code will incorporate the
         * hashes of each element of the Vector, so this operation can be quite expensive.
         * TODO consider lazy for immutable
         */
        static <CompileType extends HashableVector> Int64 hashCode(CompileType vector) {
            Int64 hash = 0;
            for (CompileType.Element el : array) {
                hash += CompileType.Element.hashCode(el);
            }
            return hash;
        }
    }


    // ----- Freezable mixin ------------------------------------------------------------------------

    private static mixin FreezableVector<Element extends Shareable>
            into Vector<Element>
            implements Freezable {
        /**
         * Return a `const` Vector of the same type and contents as this Vector.
         *
         * @param inPlace  pass True to indicate that the Vector should not make a frozen copy of
         *                 itself if it does not have to; the reason that making a copy is the
         *                 default behavior is to protect any object that already has a reference
         *                 to the unfrozen Vector
         */
        @Override
        immutable Vector freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }

            // TODO use Arrray impl

            return makeImmutable();
        }
    }
}
