# Ecstasy XVM Primitives

An Ecstasy JIT primitive is an Ecstasy type that can be represented by one or more Java primitives.
Field storage, Operations and property accessors for these types are optimized to work directly with
the underlying Java primitives, resulting in improved performance and reduced overhead.

## Adding a New XVM Primitive

The following instructions describe how to add a new XVM primitive type to the JIT.
This is the minimum that must be done. Depending on the actual type being converted to an XVM 
primitive there may be more work involved in coding the actual Java class for the type, 
especially if the type is more than a single primitive value.

### Update the Java Tools JIT bridge Module

The following updates need to be made in the `javatools` module.

1. Create a class for the XVM primitive type in corresponding package, for example the same way
that `org.xtclang.ecstasy.temporal.Duration` was created.

2. Create a `org.xtclang.ecstasy.IteratorᐸXXXᐳ` class (where XXX is the type name), for example, the
 same way that `org.xtclang.ecstasy.IteratorᐸDurationᐳ` was created.
   
3. Create an array class `org.xtclang.ecstasy.collections.ArrayᐸXXXᐳ` (where XXX is the type name).
If the XVM type is a single Java primitive, it will be simpler to just copy the corresponding 
   number array class. For example if the primitive is an `int` copy the `ArrayᐸInt32ᐳ` class.

4. Update the `$new$2()` method in `Array.java` to include the XVM primitive type in the switch
statement.

5. Update the `nRef.equals$p()` method to add a call to the XVM primitive types `$equals` method.
There are two switch statements in the `nRef.equals$p()` method, one for numeric types and one for
non-numeric XVM primitives, add code to the correct statement depending on the type.

6. Update the switch statements in the `nType.equals()` and `nType.compare()` methods to include
the new XVM primitive type.


### Update the Java Tools Module

The following updates need to be made in the `javatools_jitbridge` module.

#### ConstantPool

1. If not already present, the ConstantPool class must have a method named typeXXX() (where XXX is
the name of the type) that returns the corresponding Ecstasy type. This is just implemented the same
 way that all the other typeXXX() methods are.
2. Update the `getJitPrimitiveTypes()` method to include the new XVM primitive type

#### Builder

1.  Add a static field for the new XVM primitive type `N_XXX` where XXX is the type name, the 
    same as the existing `N_*` static fields.
2. Add a static field for the new XVM primitive array type `N_ArrayXXX` where XXX is the type 
   name, the same as the existing `N_Array*` static fields.
3. Add a static ClassDesc field for the new XVM primitive type
4. Add a static ClassDesc field for the new XVM primitive array type
5. Add a static MethodTypeDesc field for the new XVM primitive type box method
6. Update the `loadArray()` method to handle the new XVM primitive type the same as for existing
XVM primitive types.
7. Update the `unbox()` method to handle the new XVM primitive type the same as for existing XVM
primitive types.
8. Update the `box()` method to handle the new XVM primitive type the same as for existing XVM
primitive types.

#### NativeTypeSystem

1. Update the `registerNativeClasses()` method so that the `TypeConstant[] primitiveTypes` array
contains the new XVM primitive type.

#### JitTypeDesc

1. Update the getXvmPrimitiveClass() method to return the ClassDesc for the new XVM primitive
type.
2. Update the getXvmPrimitiveClasses() method to return the ClassDesc for the new XVM primitive
type.

#### ArrayBuilder

1. Update the `getArrayName()` method to include the new XVM primitive type

#### TerminalTypeConstant

1. Update the `isXvmPrimitive()` method to include the new XVM primitive type

#### CommonBuilder

1. Add the XVM primitive type to the `JIT_LIST`.


### Testing

Add tests for the new XVM primitive type to the `manualTests/src/main/x/jit` module



