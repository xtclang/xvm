package org.xvm.javajit;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;

import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.util.stream.Collectors;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.annotations.JitAnnotation;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

import static org.xvm.javajit.Builder.OPT;

/**
 * A container for a native {@link ClassModel}.
 */
public class NativeModelInfo {
    /**
     * Create a {@link NativeModelInfo} for the specified {@link ClassModel}.
     *
     * @param model  the {@link ClassModel} to wrap
     */
    public NativeModelInfo(ClassModel model) {
        this.model = model;
        this.isJitGenerated = JitAnnotation.isAnnotated(model, CD_JitGenerated);
        if (this.isJitGenerated) {
            this.jitAnnotations = model.findAttribute(Attributes.runtimeVisibleAnnotations())
                    .map(RuntimeVisibleAnnotationsAttribute::annotations)
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(ann -> (JitAnnotation) JitAnnotation.create(ann))
                    .collect(Collectors.toMap(JitAnnotation::annotationCD, a -> a));
        } else {
            this.jitAnnotations = Map.of();
            this.jitNames       = Map.of();
        }
    }

    /**
     * The wrapped {@link ClassModel}.
     */
    public final ClassModel model;

    /**
     * A flag indicating whether the class represented by the model is annotated with the
     * {@link JitGenerated} annotation.
     */
    public final boolean isJitGenerated;

    /**
     * The JIT annotations associated with the class model (i.e. annotations directly on the native
     * class).
     */
    private final Map<ClassDesc, JitAnnotation> jitAnnotations;

    /**
     * The JIT names associated with the {@link JitName} annotated methods  on the native class.
     */
    private Map<MethodKey, String> jitNames;

    private static final ClassDesc CD_JitGenerated = ClassDesc.of(JitGenerated.class.getName());
    private static final ClassDesc CD_JitName      = ClassDesc.of(JitName.class.getName());

    /**
     * @return {@code true} if the class this model represents has the specified annotation
     */
    public boolean isAnnotated(ClassDesc cd) {
        return jitAnnotations.entrySet()
                .stream()
                .anyMatch(e -> e.getKey().equals(cd));
    }

    /**
     * @return {@code true} if the class this model represents has the specified annotation
     */
    public boolean isAnnotated(Class<? extends java.lang.annotation.Annotation> clz) {
        return jitAnnotations.values()
                .stream()
                .anyMatch(clz::isInstance);
    }

    /**
     * @return the specified annotation on the class this model represents, or {@code null} if the
     *         class does not have the specified annotation.
     */
    public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> clz) {
        return jitAnnotations.values()
                .stream()
                .filter(clz::isInstance)
                .findFirst()
                .map(clz::cast)
                .orElse(null);
    }

    /**
     * @return the specified annotation on the class this model represents, or {@code null} if the
     *         class does not have the specified annotation.
     */
    @SuppressWarnings("unchecked")
    public <A extends java.lang.annotation.Annotation> A getAnnotation(ClassDesc cd) {
        return (A) jitAnnotations.entrySet()
                .stream()
                .filter(e -> e.getKey().equals(cd))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public String findReservedJitName(TypeSystem typeSystem, TypeConstant type, MethodConstant methodId) {
        if (!isJitGenerated) {
            return null;
        }

        TypeInfo          typeInfo  = type.ensureTypeInfo();
        MethodInfo        method    = typeInfo.getMethodById(methodId);
        MethodStructure   structure = method == null
                                        ? (MethodStructure) methodId.getComponent()
                                        : method.getTopmostMethodStructure(typeInfo);
        String            name      = structure.getName();
        int               flags     = JitMethodDesc.flagsFor(structure);
        SignatureConstant signature = methodId.getSignature();
        JitMethodDesc     jmd       = JitMethodDesc.of(typeSystem, type, flags,
                                                signature.getRawParams(), signature.getRawReturns(),
                                                structure.getRequiredParamCount());

        Map<MethodKey, String> jitNames = ensureJitNames();
        String                 jitName  = null;
        if (jmd.isOptimized) {
            // we try the optimized signature first because the native classes are more likely
            // to have annotated the optimized method
            jitName = jitNames.get(new MethodKey(name + OPT, jmd.optimizedMD));
        }

        if (!jmd.isOptimized || jitName == null) {
            jitName = jitNames.get(new MethodKey(name, jmd.standardMD));
        }

        return jitName;
    }

    private Map<MethodKey, String> ensureJitNames() {
        Map<MethodKey, String> jitNames = this.jitNames;
        if (jitNames == null) {
            jitNames = new HashMap<>();
            for (MethodModel method : model.methods()) {
                JitName name = JitAnnotation.create(method, CD_JitName);
                if (name != null) {
                    String naturalName = name.value();
                    String methodName  = method.methodName().stringValue();
                    if (naturalName.isEmpty()) {
                        naturalName = methodName;
                    }
                    if (methodName.endsWith(OPT)) {
                        methodName = methodName.substring(0, methodName.length() - 2);
                        if (!naturalName.endsWith(OPT)) {
                            naturalName += OPT;
                        }
                    }
                    MethodKey key = new MethodKey(naturalName, method.methodTypeSymbol());
                    jitNames.put(key, methodName);
                }
            }
            this.jitNames = jitNames;
        }
        return jitNames;
    }

    /**
     * The key for a method in the {@link #jitNames} map.
     */
    protected record MethodKey(String name, MethodTypeDesc md) {}
}
