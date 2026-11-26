package org.xvm.javajit;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AnnotationValue.OfString;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.HashMap;
import java.util.Map;

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
        this.model          = model;
        this.isJitGenerated = JitAnnotation.isAnnotated(model, CD_JitGenerated);
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
     * The JIT names associated with the {@link JitName} annotated methods  on the native class.
     */
    private Map<MethodKey, String> jitNames;

    private static final ClassDesc CD_JitGenerated = ClassDesc.of(JitGenerated.class.getName());
    private static final ClassDesc CD_JitName      = ClassDesc.of(JitName.class.getName());

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
                Annotation annotation = JitAnnotation.findAnnotation(method, CD_JitName);
                if (annotation != null) {
                    String          methodName  = method.methodName().stringValue();
                    String          naturalName = methodName;
                    AnnotationValue value       = JitAnnotation.findValue(annotation);

                    if (value != null) {
                        String name = ((OfString) value).stringValue();
                        if (!name.isEmpty()) {
                            naturalName = name;
                        }
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
