/**
 * A representation of a JSON patch, as defined
 * by https://datatracker.ietf.org/doc/html/rfc6902
 *
 * There are various rules that apply to different types of patch operation.
 * These rules are not validated until the patch operations is actually applied.
 * This means is it is possible to construct an invalid set of patches but an
 * exception will be thrown when the patches are applied.
 */
mixin JsonPatch
        into Array<Operation> {

    /**
     * Apply this patch to the specified `Doc`.
     *
     * If this patch is empty, the result returned will be the same `target` instance.
     *
     * If the target `Doc` is immutable, the result returned will be immutable.
     *
     * The `options` parameter allows non-standard behaviour to be applied to the patching
     * process. The behaviour of the default options will match the RFC6902 specification.
     *
     * @param target   the `Doc` to apply the patch to
     * @param options  the options to control patching behaviour
     *
     * @return an JSON `Doc` that is the result of applying this patch
     *         to the target JSON `Doc`
     *
     * @throws IllegalArgument if any of the operations contains an invalid parameter
     * @throws IllegalState    if applying any of the operations fails
     */
    Doc apply(Doc target, Options? options = Null) {
        if (empty) {
            return target;
        }

        Doc     doc  = target;
        Options opts = options ?: Options.Default;

        for (Operation op : this) {
            doc = switch (op.op) {
                case Add:     applyAdd(doc, op.path, op.value, opts);
                case Remove:  applyRemove(doc, op.path, opts);
                case Replace: applyReplace(doc, op.path, op.value, opts);
                case Move:    applyMove(doc, op.from, op.path, opts);
                case Copy:    applyCopy(doc, op.from, op.path, opts);
                case Test:    applyTest(doc, op.path, op.value, opts);
                default:      assert;
            };
        }

        switch (doc.is(_)) {
            case JsonObject:
                if (!doc.inPlace) {
                    doc.makeImmutable();
                }
                break;
            case JsonArray:
                if (!doc.inPlace) {
                    doc.makeImmutable();
                }
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
    static Builder builder() = new Builder();

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Perform an add operation.
     *
     * @param target   the JSON value to perform the add operation on
     * @param path     the path specifying the location in the target to add value to
     * @param value    the JSON value to add
     * @param options  the options to control patching behaviour
     */
    private Doc applyAdd(Doc target, JsonPointer path, Doc value, Options options) {
        return switch (target.is(_)) {
            case JsonObject: applyAddToObject(target, path, value, options);
            case JsonArray:  applyAddToArray(target, path, value, options);
            case Primitive:  applyAddToPrimitive(target, path, value);
            default:         assert as $"Invalid JSON type {&target.actualType}";
        };
    }

    /**
     * Perform an add operation on a `JsonObject`.
     *
     * @param target   the `JsonObject` to perform the add operation on
     * @param path     the path specifying the location in the target to add value to
     * @param value    the JSON value to add
     * @param options  the options to control patching behaviour
     */
    private Doc applyAddToObject(JsonObject obj, JsonPointer path, Doc value, Options options) {
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
                mutable.put(path.key, applyAdd(doc, remainder, value, options));
            } else {
                if (options.ensurePathExistsOnAdd) {
                    // we are allowing missing elements on add, we just need to add a new object
                    mutable.put(path.key, applyAdd(json.newObject(), remainder, value, options));
                } else {
                    assert:arg as $"Cannot perform add operation on JSON object for path {path}, missing key '{path.key}'";
                }
            }
        } else {
            mutable.put(path.key, value);
        }
        return mutable;
    }

    /**
     * Perform an add operation on a `JsonArray`.
     *
     * @param target   the `JsonArray` to perform the add operation on
     * @param path     the path specifying the location in the target to add value to
     * @param value    the JSON value to add
     * @param options  the options to control patching behaviour
     */
    private Doc applyAddToArray(JsonArray array, JsonPointer path, Doc value, Options options) {
        JsonArray mutable;
        if (array.mutability == Mutable) {
            mutable = array;
        } else {
            mutable = json.newArray();
            mutable.addAll(array);
        }

        if (path.key == JsonPointer.AppendKey) {
            mutable.add(value);
            return mutable;
        }

        assert:arg Int index := path.getValidIndex(array, options.supportNegativeIndices)
                as $"Cannot perform add operation on JSON array, path {path} is not an array index";

        JsonPointer? remainder = path.remainder;
        if (remainder.is(JsonPointer)) {
            Doc doc = array[index];
            mutable[index] = applyAdd(doc, remainder, value, options);
        } else {
            if (index == mutable.size) {
                mutable.add(value);
            } else {
                mutable.insert(index, value);
            }
        }
        return mutable;
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
     * @param target   the JSON value to perform the remove operation on
     * @param path     the path specifying the location in the target to remove
     * @param options  the options to control patching behaviour
     */
    private Doc applyRemove(Doc target, JsonPointer path, Options options) {
        return switch (target.is(_)) {
            case JsonObject: applyRemoveFromObject(target, path, options);
            case JsonArray:  applyRemoveFromArray(target, path, options);
            case Primitive:  applyRemoveFromPrimitive(target, path);
            default:         assert as "invalid JSON type {&doc.actualType}";
        };
    }

    /**
     * Perform an remove operation on a `JsonObject`.
     *
     * @param target   the `JsonObject` to perform the remove operation on
     * @param path     the path specifying the location in the target to remove
     * @param options  the options to control patching behaviour
     */
    private Doc applyRemoveFromObject(JsonObject obj, JsonPointer path, Options options) {
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
                mutable.put(path.key, applyRemove(doc, remainder, options));
            } else {
                mutable.remove(path.key);
            }
            return mutable;
        }
        // there is no element in the JsonObject for the path key
        if (options.allowMissingPathOnRemove) {
            // we are allowing missing elements on remove so return the unmodified original object
            return obj;
        }
        assert:arg as $"Cannot perform remove operation on JSON object, missing key '{path.key}' from path '{path}'";
    }

    /**
     * Perform an remove operation on a `JsonArray`.
     *
     * @param target   the `JsonArray` to perform the remove operation on
     * @param path     the path specifying the location in the target to remove
     * @param options  the options to control patching behaviour
     */
    private Doc applyRemoveFromArray(JsonArray array, JsonPointer path, Options options) {
        assert:arg path.key != JsonPointer.AppendKey as "Cannot use append key '-' in a remove operation on a JSON array";
        assert:arg Int index := path.getValidIndex(array, options.supportNegativeIndices)
                as $"Cannot perform remove operation on JSON array, path {path} is not an array index";

        Int size = array.size;
        // valid index is 0 ..< array.size || -array.size >.. -1
        if (index >= size || index <= -size) {
            if (options.allowMissingPathOnRemove) {
                // the index is out of bounds, but the options allow this, so return the original, unmodified array
                return array;
            } else {
                // we are not allowing invalid indexes
                assert:arg  as $|Cannot perform remove operation on JSON array, \
                                | index {index} out of bounds 0 ..< {size}
                                ;
            }
        }

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
            mutable[index] = applyRemove(doc, remainder, options);
        } else {
            mutable.delete(index);
        }
        return mutable;
    }

    /**
     * Remove the primitive pointed to by the path
     *
     * @param p     the `Primitive` to be removed
     * @param path  the `JsonPointer` pointing to the primitive
     *
     * @throws IllegalArgument iff the pointer is not a leaf pointer
     */
    private Doc applyRemoveFromPrimitive(Primitive p, JsonPointer path) {
        assert:arg path.isEmpty as $"Cannot perform remove operation on primitive value {p} path '{path}' is not a leaf";
        return Null;
    }

    /**
     * Perform a replace operation.
     *
     * @param target   the JSON value to perform the replace operation on
     * @param path     the path specifying the location in the target to replace
     * @param value    the new JSON value to replace the target value
     * @param options  the options to control patching behaviour
     */
    private Doc applyReplace(Doc target, JsonPointer path, Doc value, Options options) {
        return switch (target.is(_)) {
            case JsonObject: applyReplaceToObject(target, path, value, options);
            case JsonArray:  applyReplaceToArray(target, path, value, options);
            case Primitive:  applyReplaceToPrimitive(target, path, value);
            default:         assert as $"Invalid JSON type {&target.actualType}";
        };
    }

    /**
     * Perform a replace operation on a `JsonObject`.
     *
     * @param target   the `JsonObject` to perform the replace operation on
     * @param path     the path specifying the location in the target to replace
     * @param value    the new JSON value to replace the target value
     * @param options  the options to control patching behaviour
     */
    private Doc applyReplaceToObject(JsonObject obj, JsonPointer path, Doc value, Options options) {
        JsonPointer? remainder = path.remainder;
        JsonObject   mutable;

        if (obj.inPlace) {
            mutable = obj;
        } else {
            mutable = json.newObject();
            mutable.putAll(obj);
        }
        if (remainder.is(JsonPointer)) {
            assert Doc doc := mutable.get(path.key) as $|Cannot perform replace operation on JSON object \
                                                        | for path '{path}', missing key '{path.key}'
                                                        ;

            mutable.put(path.key, applyReplace(doc, remainder, value, options));
        } else {
            assert:arg mutable.contains(path.key) as $|Cannot perform replace operation on JSON object \
                                                 | for path '{path}', missing key '{path.key}'
                                                 ;
            mutable.put(path.key, value);
        }
        return mutable;
    }

    /**
     * Perform an replace operation on a `JsonArray`.
     *
     * @param target   the `JsonArray` to perform the replace operation on
     * @param path     the path specifying the location in the target to replace
     * @param value    the new JSON value to replace the target value
     * @param options  the options to control patching behaviour
     */
    private Doc applyReplaceToArray(JsonArray array, JsonPointer path, Doc value, Options options) {
        assert:arg path.key != JsonPointer.AppendKey as "Cannot use append key '-' in a replace operation on a JSON array";

        assert:arg Int index := path.getValidIndex(array, options.supportNegativeIndices)
                as $"Cannot perform replace operation on JSON array, path {path} is not an array index";

        JsonPointer? remainder = path.remainder;
        JsonArray    mutable;

        if (array.mutability == Mutable) {
            mutable = array;
        } else {
            mutable = json.newArray();
            mutable.addAll(array);
        }

        if (remainder.is(JsonPointer)) {
            Doc doc = mutable[index];
            mutable[index] = applyReplace(doc, remainder, value, options);
        } else {
            mutable.replace(index, value);
        }
        return mutable;
    }

    /**
     * Perform an replace operation on a `Primitive`.
     *
     * @param target  the `Primitive` to perform the replace operation on
     * @param path    the path specifying the location in the target to replace
     * @param value   the new JSON value to replace the target value
     *
     * @throws IllegalArgument if the path argument is not a leaf pointer
     */
    private Doc applyReplaceToPrimitive(Primitive p, JsonPointer path, Doc value) {
        assert:arg path.isEmpty as $"Cannot perform replace operation on primitive value {p} path {path} is not a leaf";
        return value;
    }

    /**
     * Perform an move operation.
     *
     * @param target   the JSON value to perform the move operation on
     * @param from     the `JsonPointer` specifying the location in the value to move
     * @param to       the `JsonPointer` specifying the destination of the moved value
     * @param options  the options to control patching behaviour
     */
    private Doc applyMove(Doc target, JsonPointer? from, JsonPointer to, Options options) {
        assert:arg from.is(JsonPointer) as "A move operation must have a 'from' JSON pointer";
        assert:arg !from.isParent(to) as $|Invalid move operation, the from location "{from}" \
                                          | cannot be a parent of the destination path "{to}"
                                          ;

        assert Doc toMove := from.get(target, options.supportNegativeIndices)
                as $"Move operation failed, no value exists at location '{from}'";
        return applyAdd(applyRemove(target, from, options), to, toMove, options);
    }

    /**
     * Perform an copy operation.
     *
     * @param target   the JSON value to perform the copy operation on
     * @param from     the `JsonPointer` specifying the location in the value to copy
     * @param to       the `JsonPointer` specifying the destination of the copied value
     * @param options  the options to control patching behaviour
     */
    private Doc applyCopy(Doc target, JsonPointer? from, JsonPointer to, Options options) {
        assert:arg from.is(JsonPointer) as "A copy operation must have a 'from' JSON pointer";
        assert Doc toCopy := from.get(target, options.supportNegativeIndices)
                as $"Copy operation failed, no value exists at location '{from}'";
        return applyAdd(target, to, toCopy, options);
    }

    /**
     * Perform a test operation.
     *
     * @param target    the JSON value to perform the add operation on
     * @param path      the path specifying the location in the target to add value to
     * @param expected  the JSON value to add
     * @param options   the options to control patching behaviour
     *
     * @return the unmodified `target`
     *
     * @throws IllegalState iff the value in the `target` at the specified `path` is not equal to the `expected` value
     */
    private Doc applyTest(Doc target, JsonPointer path, Doc expected, Options options) {
        if (Doc existing := path.get(target)) {
            assert existing == expected as $|Test operation failed, value at location "{path}" is "{existing}"\
                       | but expected "{expected}"
                       ;
            return target;
        }
        if (expected == Null) {
            return target;
        }
        assert as $|Test operation failed, value at location "{path}" is Null \
                   | but expected value "{expected}"
                   ;
    }

    /**
     * Validate an array index, converting negative indexes into their corresponding positive value.
     *
     * If `options.supportNegativeIndices` is `True` negative indexes are allowed. In this case -1 refers
     * to the last element in the array, -2 the second to last, and so on up to -(array.size - 1) which
     * refers to the element at index zero.
     *
     * - If the index is zero or positive, it must be in the range 0 ..< array.size
     * - If the index is negative, `options.supportNegativeIndices` must be `True` and the index
     *   must be in the range -array.size .. -1
     *
     * @param op       the `Action` being executed
     * @param index    the index to validate
     * @param array    the array the index is for
     * @param options  the options controlling the operation
     *
     * @return the valid index (negative values will have been converted to the correct positive index)
     */
    private Int validateIndex(Action op, Int index, JsonArray array, Options options) {
        if (index >= 0 && index < array.size) {
            // index is valid
            return index;
        }

        if (index > 0) {
            // invalid positive index
            assert:arg as $"Cannot perform {op} on JSON array, index {index} out of bounds, valid range 0 ..< {array.size}";
        }

        // index is negative
        if (options.supportNegativeIndices) {
            // We allow negative indexes, which means the index counts
            // from the end of the array, so valid values are [-array.size >.. 0]
            // We already know we are not zero
            if (index < -array.size) {
                // the index is too negative
                assert:arg as $|Cannot perform {op} on JSON array, negative array index \
                               | {index} out of bounds, expected -{array.size} .. -1
                               ;
            }
            return index + array.size;
        }
        // negative indexes not allowed
        assert:arg as $|Cannot perform {op} on JSON array, negative array index {index} not allowed, \
                       | valid range 0 ..< {array.size}
                       ;
    }

    // ----- Operation inner class -----------------------------------------------------------------

    /**
     * An operation in a JSON patch that takes a value.
     *
     * @param op     the action the operation will perform
     * @param path   the path to the value to apply the action to
     * @param value  the value the action will apply (ignored for copy, move, or remove operations)
     * @param from   the path to the "from" value (required for copy or move operations, ignored for other operations)
     */
    static const Operation(Action op, JsonPointer path, Doc value = Null, JsonPointer? from = Null) {

        // ----- equality support ------------------------------------------------------------------

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
            return switch (doc1.is(_)) {
                case JsonObject: doc2.is(JsonObject) && doc1 == doc2;
                case JsonArray:  doc2.is(JsonArray)  && doc1 == doc2;
                case Primitive:  doc2.is(Primitive)  && doc1 == doc2;
                default: assert;
            };
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


    // ----- Options inner class -------------------------------------------------------------------

    /**
     * A set of options that can be used to control the patching behaviour. These options typically change the
     * behavior from the standard specified by https://datatracker.ietf.org/doc/html/rfc6902
     *
     * @param ensurePathExistsOnAdd     a flag to indicate that an add operation should recursively create the missing
     *                                  parts of path. For example adding "/foo/bar" if "foo" does not exist a JSON
     *                                  object will be added at key "foo" and then "bar" will be added to that object.
     * @param allowMissingPathOnRemove  a flag to indicate that remove operations should not fail if the target path is
     *                                  missing. The default is `False`
     * @param supportNegativeIndices    support the non-standard use of negative indices for JSON arrays to mean indices
     *                                  starting at the end of an array. For example, -1 points to the last element in
     *                                  the array. Valid negative indices are -1 ..< -array.size The default is `False`
     */
    static const Options(Boolean ensurePathExistsOnAdd    = False,
                         Boolean allowMissingPathOnRemove = False,
                         Boolean supportNegativeIndices   = False) {
        /**
         * The default options.
         */
        static Options Default = new Options();
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
        Int size.get() = ops.size;

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
        Builder add(String path, Doc value) = add(JsonPointer.from(path), value);

        /**
         * Add an "add" operation to the builder.
         *
         * @param path   the path to the value to add
         * @param value  the JSON value to add
         *
         * @return this `Builder`
         */
        Builder add(JsonPointer path, Doc value) = withOperation(new Operation(Add, path, value));

        /**
         * Add a "remove" operation to the builder.
         *
         * @param path   the path to the value to remove
         *
         * @return this `Builder`
         */
        Builder remove(String path) = remove(JsonPointer.from(path));

        /**
         * Add a "remove" operation to the builder.
         *
         * @param path   the path to the value to remove
         *
         * @return this `Builder`
         */
        Builder remove(JsonPointer path) = withOperation(new Operation(Remove, path));

        /**
         * Add a "replace" operation to the builder.
         *
         * @param path   the path to the value to replace
         * @param value  the new JSON value to add at the path location
         *
         * @return this `Builder`
         */
        Builder replace(String path, Doc value) = replace(JsonPointer.from(path), value);

        /**
         * Add a "replace" operation to the builder.
         *
         * @param path   the path to the value to replace
         * @param value  the new JSON value to add at the path location
         *
         * @return this `Builder`
         */
        Builder replace(JsonPointer path, Doc value) = withOperation(new Operation(Replace, path, value));

        /**
         * Add a "move" operation to the builder.
         *
         * @param from  the path to the value to move
         * @param to    the path to the new location to add the value being moved
         *
         * @return this `Builder`
         */
        Builder move(String from, String to) = move(JsonPointer.from(from), JsonPointer.from(to));

        /**
         * Add a "move" operation to the builder.
         *
         * @param from  the path to the value to move
         * @param to    the path to the new location to add the value being moved
         *
         * @return this `Builder`
         */
        Builder move(JsonPointer from, JsonPointer to) = withOperation(new Operation(Move, to, Null, from));

        /**
         * Add a "copy" operation to the builder.
         *
         * @param from  the path to the value to copy
         * @param to    the path to the location to add the copied value
         *
         * @return this `Builder`
         */
        Builder copy(String from, String to) = copy(JsonPointer.from(from), JsonPointer.from(to));

        /**
         * Add a "copy" operation to the builder.
         *
         * @param from  the path to the value to copy
         * @param to    the path to the location to add the copied value
         *
         * @return this `Builder`
         */
        Builder copy(JsonPointer from, JsonPointer to) = withOperation(new Operation(Copy, to, Null, from));

        /**
         * Add a "test" operation to the builder.
         *
         * @param path  the path to the value to test
         * @param value the expected value
         *
         * @return this `Builder`
         */
        Builder test(String path, Doc value) = test(JsonPointer.from(path), value);

        /**
         * Add a "test" operation to the builder.
         *
         * @param path  the path to the value to test
         * @param value the expected value
         *
         * @return this `Builder`
         */
        Builder test(JsonPointer path, Doc value) = withOperation(new Operation(Test, path, value));

        /**
         * Build an immutable `JsonPatch`.
         *
         * @return an immutable `JsonPatch`
         */
        JsonPatch build() = JsonPatch.create(ops);
    }
}
