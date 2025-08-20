# ByteBuddy Migration Plan

## Current Status
✅ **Dependencies**: ByteBuddy added to version catalog and javatools  
✅ **Analysis**: Complete analysis document created  
✅ **Foundation**: ByteBuddyBuilder and ByteBuddyTypeSystem created  
❌ **Java 21 Compilation**: 100+ compilation errors due to ClassFile API usage  

## Migration Strategy

### Phase 1: Conditional Compilation (Current Branch)
Create a dual-implementation approach where:
- Java 21: Uses ByteBuddy implementation  
- Java 24+: Uses ClassFile API implementation  
- Feature flag to control which implementation is used  

### Phase 2: Complete Migration (Future)
Replace all ClassFile API usage with ByteBuddy equivalents.

## Immediate Next Steps (This Branch)

### 1. Create Conditional JIT Loading
```java
public class JitImplementationFactory {
    private static final boolean USE_BYTEBUDDY = 
        !isClassFileApiAvailable() || Boolean.getBoolean("xvm.jit.useBytebuddy");
    
    public static TypeSystem createTypeSystem() {
        return USE_BYTEBUDDY ? 
            new ByteBuddyTypeSystem() : 
            new ClassFileTypeSystem();
    }
    
    private static boolean isClassFileApiAvailable() {
        try {
            Class.forName("java.lang.classfile.ClassFile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

### 2. Isolate ClassFile API Code
Move all ClassFile API dependent code to separate classes that only compile on Java 24+:
- `ClassFileTypeSystem` (renamed from `TypeSystem`)
- `ClassFileBuilder` (renamed from `Builder`)  
- All existing `builders/*` classes

### 3. Update Build Logic
Configure compilation to:
- Always include ByteBuddy classes
- Only include ClassFile classes when Java 24+ is available
- Use reflection to load the appropriate implementation

## Benefits of This Approach

1. **Immediate Java 21 Support**: ByteBuddy implementation works on Java 21
2. **No Breaking Changes**: Existing Java 24 users continue to work
3. **Safe Migration**: Can gradually migrate features to ByteBuddy
4. **Performance Testing**: Can compare implementations side-by-side

## Files That Need Migration

### Core Files (100+ compilation errors)
- `Builder.java` → `ClassFileBuilder.java` + `ByteBuddyBuilder.java`
- `TypeSystem.java` → `ClassFileTypeSystem.java` + `ByteBuddyTypeSystem.java`  
- `ModuleLoader.java` → needs ByteBuddy equivalent
- `NativeTypeSystem.java` → needs ByteBuddy equivalent

### Builder Classes (20+ files)
- All files in `org.xvm.javajit.builders.*`
- Each needs ByteBuddy equivalent

### Op.java
- Single ClassFile API import that can be conditionally compiled

## Recommended Implementation Order

1. **Create factory pattern** for JIT implementation selection
2. **Rename existing classes** to `ClassFile*` variants  
3. **Create minimal ByteBuddy implementations** that compile on Java 21
4. **Test basic functionality** on both Java 21 and Java 24
5. **Gradually enhance ByteBuddy implementations**

## Testing Strategy

```bash
# Test Java 21 compatibility
./gradlew test -Porg.xtclang.java.jdk=21 -Dxvm.jit.useBytebuddy=true

# Test Java 24 compatibility (both implementations)
./gradlew test -Porg.xtclang.java.jdk=24 -Dxvm.jit.useBytebuddy=false
./gradlew test -Porg.xtclang.java.jdk=24 -Dxvm.jit.useBytebuddy=true
```

## Long-term Vision

Eventually, the ByteBuddy implementation should become the primary implementation due to:
- Better Java version compatibility  
- More stable APIs
- Richer feature set  
- Larger community support

The ClassFile API implementation could be deprecated once ByteBuddy implementation reaches feature parity.