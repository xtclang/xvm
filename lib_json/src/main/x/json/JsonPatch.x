/**
 * A representation of a JSON patch, as defined
 * by https://datatracker.ietf.org/doc/html/rfc6902
 */
mixin JsonPatch
        into Array<Operation> {

    /**
     * Apply this patch to the specified `Doc`.
     *
     * @param doc  the `Doc§ to apply the patch to
     *
     * @return the patched `Doc`
     */
    Doc apply(Doc doc) {
        for (Operation op : this) {
            doc = switch (op.op) {
                case Add: applyAdd(doc, op.path, op.value);
                default:  assert;
            };
        }
        switch (doc.is(_)) {
            case JsonObject:
                doc.makeImmutable();
                break;
            case JsonArray:
                doc.makeImmutable();
                break;
        }
        return doc;
    }

    // ----- JsonPatch factory methods -------------------------------------------------------------

    /**
     * Create an immutable `JsonPatch` from an array of `Operation`s.
     *
     * @param ops  the operations to add to the `JsonPatch`
     *
     * @return an immutable `JsonPatch` that will apply the specified operations
     */
    static JsonPatch create(Operation[] ops) {
        JsonPatch patch = new @JsonPatch Array<Operation>();
        patch.addAll(ops);
        return patch.makeImmutable();
    }

    /**
     * Create a `JsonPatch` builder.
     *
     * @return a `JsonPatch` builder
     */
    static Builder builder() {
        return new Builder();
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Perform an add operation.
     *
     * @param target  the JSON value to perform the add operation on
     * @param path    the path specifying the location in the target to add value to
     * @param value   the JSON value to add
     */
    private Doc applyAdd(Doc target, JsonPointer path, Doc value) {
        return switch (target.is(_)) {
            case JsonObject: applyAddToObject(target, path, value);
            case JsonArray:  applyAddToArray(target, path, value);
            case Primitive:  applyAddToPrimitive(target, path, value);
            default:         assert as "invalid JSON type {&doc.actualType}";
        };
    }

    /**
     * Perform an add operation on a `JsonObject`.
     *
     * @param target  the `JsonObject` to perform the add operation on
     * @param path    the path specifying the location in the target to add value to
     * @param value   the JSON value to add
     */
    private Doc applyAddToObject(JsonObject obj, JsonPointer path, Doc value) {
        JsonPointer? remainder = path.remainder;
        JsonObject   mutable;

        if (obj.inPlace) {
            mutable = obj;
        } else {
            mutable = json.newObject();
            mutable.putAll(obj);
        }
        if (remainder.is(JsonPointer)) {
            if (Doc doc := mutable.get(path.key)) {
                mutable.put(path.key, applyAdd(doc, remainder, value));
            } else {
                throw new IllegalState($"Cannot perform add operation on JSON object for path {path}, missing key '{path.key}'");
            }
        } else {
            mutable.put(path.key, value);
        }
        return mutable;
    }

    /**
     * Perform an add operation on a `JsonArray`.
     *
     * @param target  the `JsonArray` to perform the add operation on
     * @param path    the path specifying the location in the target to add value to
     * @param value   the JSON value to add
     */
    private Doc applyAddToArray(JsonArray array, JsonPointer path, Doc value) {
        Int? index = path.index;
        if (index.is(Int)) {
            JsonPointer? remainder = path.remainder;
            JsonArray    mutable;
            if (array.mutability == Mutable) {
                mutable = array;
            } else {
                mutable = json.newArray();
                mutable.addAll(array);
            }
            if (remainder.is(JsonPointer)) {
                Doc doc = array[index];
                mutable[index] = applyAdd(doc, remainder, value);
            } else {
                // if the index is the AppendIndex or it is the array size,
                // then add the value, else update the value at the index
                if (index == JsonPointer.AppendIndex || index == mutable.size) {
                    mutable.add(value);
                } else {
                    mutable.replace(index, value);
                }
            }
            return mutable;
        }
        throw new IllegalArgument($"Cannot perform remove operation on JSON array, path {path} is not an array index");
    }

    /**
     * Perform an add operation on a `Primitive`.
     *
     * @param target  the `Primitive` to perform the add operation on
     * @param path    the path specifying the location in the target to add value to
     * @param value   the JSON value to add
     *
     * @throws IllegalArgument if the path argument is not a leaf pointer
     */
    private Doc applyAddToPrimitive(Primitive p, JsonPointer path, Doc value) {
        assert:arg path.isEmpty as $"Cannot perform add operation on primitive value {p} path {path} is not a leaf";
        return value;
    }

    /**
     * Perform an remove operation.
     *
     * @param target  the JSON value to perform the remove operation on
     * @param path    the path specifying the location in the target to remove
     */
    private Doc applyRemove(Doc target, JsonPointer path) {
        return switch (target.is(_)) {
            case JsonObject: applyRemoveFromObject(target, path, value);
            case JsonArray:  applyRemoveFromArray(target, path, value);
            case Primitive:  applyRemovePrimitive(target, path, value);
            default:         assert as "invalid JSON type {&doc.actualType}";
        };
    }

    /**
     * Perform an remove operation on a `JsonObject`.
     *
     * @param target  the `JsonObject` to perform the remove operation on
     * @param path    the path specifying the location in the target to remove
     */
    private Doc applyRemoveFromObject(JsonObject obj, JsonPointer path) {
        JsonPointer? remainder = path.remainder;
        JsonObject   mutable;

        if (obj.inPlace) {
            mutable = obj;
        } else {
            mutable = json.newObject();
            mutable.putAll(obj);
        }
        if (Doc doc := mutable.get(path.key)) {
            if (remainder.is(JsonPointer)) {
                mutable.put(path.key, applyRemove(doc, remainder, value));
            } else {
                mutable.remove(path.key);
            }
        } else {
            throw new IllegalState($"Cannot perform remove operation on JSON object, missing key '{path.key}'");
        }
        return mutable;
    }

    /**
     * Perform an remove operation on a `JsonArray`.
     *
     * @param target  the `JsonArray` to perform the remove operation on
     * @param path    the path specifying the location in the target to remove
     * @param value   the JSON value to remove
     */
    private Doc applyRemoveFromArray(JsonArray array, JsonPointer path, Doc value) {
        Int? index = path.index;
        if (index.is(Int)) {
            JsonPointer? remainder = path.remainder;
            JsonArray    mutable;
            if (array.mutability == Mutable) {
                mutable = array;
            } else {
                mutable = json.newArray();
                mutable.removeAll(array);
            }
            if (remainder.is(JsonPointer)) {
                Doc doc = array[index];
                mutable[index] = applyRemove(doc, remainder, value);
            } else {
                // if the index is the AppendIndex or it is the array size,
                // then remove the value, else update the value at the index
                if (index == JsonPointer.AppendIndex || index == mutable.size) {
                    mutable.remove(value);
                } else {
                    mutable.replace(index, value);
                }
            }
            return mutable;
        }
        throw new IllegalArgument($"Cannot perform remove operation on JSON array, path {path} is not an array index");
    }

    /**
     * Perform an remove operation on a `Primitive`.
     *
     * @param target  the `Primitive` to perform the remove operation on
     * @param path    the path specifying the location in the target to remove
     * @param value   the JSON value to remove
     *
     * @throws IllegalArgument if the path argument is not a leaf pointer
     */
    private Doc applyRemovePrimitive(Primitive p, JsonPointer path, Doc value) {
        assert:arg path.isEmpty as $"Cannot perform remove operation on primitive value {p} path {path} is not a leaf";
        return value;
    }

    // ----- Operation inner class -----------------------------------------------------------------

    /**
     * An operation in a JSON patch that takes a value.
     */
    static const Operation {
        /**
         * Create an JSON patch `Operation`.
         *
         * @param op     the action the operation will perform
         * @param path   the path to the value to apply the action to
         * @param value  the value the action will apply (ignored for copy, move, or remove operations)
         * @param from   the path to the "from" value (required for copy or move operations, ignored for other operations)
         *
         * @throws IllegalArgument if the operation is a copy or a move and the from parameter is null
         */
        construct (Action op, JsonPointer path, Doc value = Null, JsonPointer? from = Null) {
            this.op = op;
            this.path = path;
            if (op == Copy || op == Move) {
                assert:arg from != Null as $"A {op} operation must have a from JSON pointer";
            }
            this.value = value;
            this.from = from;
        }

        /**
         * The action this operation performs.
         */
        Action op;

        /**
         * The path to the target element to perform the operation on.
         */
        JsonPointer path;

        /**
         * The value to use (ignored for copy or move operations).
         */
        Doc value;

        /**
         * The source value for a copy or move operation.
         */
        JsonPointer? from;

        static <CompileType extends Operation> Boolean equals(CompileType value1, CompileType value2) {
            if (value1.op != value2.op) {
                return False;
            }
            if (value1.path != value2.path) {
                return False;
            }
            if (value1.from != value2.from) {
                return False;
            }

            Doc doc1 = value1.value;
            Doc doc2 = value2.value;
            switch (doc1.is(_)) {
            case JsonObject:
                if (doc2.is(JsonObject)) {
                    return doc1 == doc2;
                }
                return False;
            case JsonArray:
                if (doc2.is(JsonArray)) {
                    return doc1 == doc2;
                }
                return False;
            case Primitive:
                if (doc2.is(Primitive)) {
                    return doc1 == doc2;
                }
                return False;
            default:
                assert;
            }
        }

    }

    // ----- Action inner enum ---------------------------------------------------------------------

    /**
     * An enum representing the valid operations in a JSON patch.
     *
     * see https://datatracker.ietf.org/doc/html/rfc6902#section-4
     */
    enum Action(String jsonName) {
        /**
         * An Add operation, see https://datatracker.ietf.org/doc/html/rfc6902#section-4.1
         *
         * The "add" operation performs one of the following functions, depending upon what
         * the target location references.
         */
        Add("add"),
        /**
         * A Remove operation, see https://datatracker.ietf.org/doc/html/rfc6902#section-4.2
         *
         * The "remove" operation removes the value at the target location. The target location
         * MUST exist for the operation to be successful.
         */
        Remove("remove"),
        /**
         * A Replace operation, see https://datatracker.ietf.org/doc/html/rfc6902#section-4.3
         *
         * The "replace" operation replaces the value at the target location with a new value.
         *
         * The operation object MUST contain a "value" member whose content specifies the replacement
         * value. The target location MUST exist for the operation to be successful.
         */
        Replace("replace"),
        /**
         * A Move operation, see https://datatracker.ietf.org/doc/html/rfc6902#section-4.4
         *
         * The "move" operation removes the value at a specified location and adds it to the target location.
         *
         * The operation object MUST contain a "from" member, which is a string containing a JSON Pointer
         * value that references the location in the target document to move the value from.
         *
         * The "from" location MUST exist for the operation to be successful.
         *
         * The "from" location MUST NOT be a proper prefix of the "path" location;
         * i.e., a location cannot be moved into one of its children.
         */
        Move("move"),
        /**
         * A Copy operation, see https://datatracker.ietf.org/doc/html/rfc6902#section-4.5
         *
         * The "copy" operation copies the value at a specified location to the target location.
         *
         * The operation object MUST contain a "from" member, which is a string containing a
         * JSON Pointer value that references the location in the target document to copy the
         * value from.
         *
         * The "from" location MUST exist for the operation to be successful.
         *
         * This operation is functionally identical to an "add" operation at the target location
         * using the value specified in the "from" member.
         */
        Copy("copy"),
        /**
         * A Test operation, see https://datatracker.ietf.org/doc/html/rfc6902#section-4.6
         *
         * The "test" operation tests that a value at the target location is equal to a specified value.
         *
         * The operation object MUST contain a "value" member that conveys the value to be compared to
         * the target location's value.
         *
         * The target location MUST be equal to the "value" value for the operation to be considered successful.
         * Here, "equal" means that the value at the target location and the value conveyed by "value" are of
         * the same JSON type, and that they are considered equal by the rules for that type.
         */
        Test("test"),
    }


    // ----- Builder inner class -------------------------------------------------------------------

    /**
     * A builder that can build immutable instance of a `JsonPatch`
     */
    static class Builder {

        /**
         * The array of operations that the JSON patch will contain.
         */
        private Array<Operation> ops = new Array();

        /**
         * The number of operations the builder contains.
         */
        Int size.get() {
            return ops.size;
        }

        /**
         * Add an operation to the builder.
         *
         * @param op  the `Operation` to add
         *
         * @return this `Builder`
         */
        Builder withOperation(Operation op) {
            ops.add(op);
            return this;
        }

        /**
         * Add an "add" operation to the builder.
         *
         * @param path   the path to the value to add
         * @param value  the JSON value to add
         *
         * @return this `Builder`
         */
        Builder add(String path, Doc value) {
            return add(JsonPointer.from(path), value);
        }

        /**
         * Add an "add" operation to the builder.
         *
         * @param path   the path to the value to add
         * @param value  the JSON value to add
         *
         * @return this `Builder`
         */
        Builder add(JsonPointer path, Doc value) {
            return withOperation(new Operation(Add, path, value));
        }

        /**
         * Add a "remove" operation to the builder.
         *
         * @param path   the path to the value to remove
         *
         * @return this `Builder`
         */
        Builder remove(JsonPointer path) {
            return withOperation(new Operation(Remove, path));
        }

        /**
         * Add a "replace" operation to the builder.
         *
         * @param path   the path to the value to replace
         * @param value  the new JSON value to add at the path location
         *
         * @return this `Builder`
         */
        Builder replace(JsonPointer path, Doc value) {
            return withOperation(new Operation(Replace, path, value));
        }

        /**
         * Add a "move" operation to the builder.
         *
         * @param from  the path to the value to move
         * @param to    the path to the new location to add the value being moved
         *
         * @return this `Builder`
         */
        Builder move(JsonPointer from, JsonPointer to) {
            return withOperation(new Operation(Move, to, Null, from));
        }

        /**
         * Add a "copy" operation to the builder.
         *
         * @param from  the path to the value to copy
         * @param to    the path to the location to add the copied value
         *
         * @return this `Builder`
         */
        Builder copy(JsonPointer from, JsonPointer to) {
            return withOperation(new Operation(Copy, to, Null, from));
        }

        /**
         * Add a "test" operation to the builder.
         *
         * @param path  the path to the value to test
         * @param value the expected value
         *
         * @return this `Builder`
         */
        Builder test(JsonPointer path, Doc value) {
            return withOperation(new Operation(Test, path, value));
        }

        /**
         * Build an immutable `JsonPatch`.
         *
         * @return an immutable `JsonPatch`
         */
        JsonPatch build() {
            return JsonPatch.create(ops);
        }
    }
}
