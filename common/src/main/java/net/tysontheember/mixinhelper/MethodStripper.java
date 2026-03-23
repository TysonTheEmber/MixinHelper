package net.tysontheember.mixinhelper;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * ASM bytecode manipulation for surgically removing or no-oping methods
 * from target classes after mixins have been applied.
 */
public final class MethodStripper {

    private MethodStripper() {}

    /**
     * Process all method removal rules that match the given target class.
     */
    public static void processClassNode(ClassNode targetClass, String targetClassName,
                                        MixinHelperConfig config) {
        if (config.methodRemovals == null || config.methodRemovals.rules == null) {
            return;
        }

        for (MixinHelperConfig.MethodRemovalRule rule : config.methodRemovals.rules) {
            if (!targetClassName.equals(rule.targetClass) &&
                !targetClassName.equals(rule.targetClass.replace('.', '/'))) {
                continue;
            }

            // Guardrail check: refuse to strip methods on protected classes
            if (!Guardrails.checkTargetClass(rule.targetClass,
                    "Method " + (rule.action != null ? rule.action : "nop")
                    + " on method '" + (rule.method != null ? rule.method : rule.methodPattern) + "'",
                    config)) {
                continue;
            }

            applyRule(targetClass, targetClassName, rule, config.debug);
        }
    }

    private static void applyRule(ClassNode targetClass, String targetClassName,
                                  MixinHelperConfig.MethodRemovalRule rule,
                                  MixinHelperConfig.DebugConfig debug) {
        Pattern pattern = null;
        if (rule.methodPattern != null && !rule.methodPattern.isEmpty()) {
            try {
                pattern = Pattern.compile(rule.methodPattern);
            } catch (Exception e) {
                Log.error("Invalid method pattern '" + rule.methodPattern + "': " + e.getMessage());
                return;
            }
        }

        Iterator<MethodNode> it = targetClass.methods.iterator();
        while (it.hasNext()) {
            MethodNode method = it.next();

            if (!matchesMethod(method, rule, pattern)) {
                continue;
            }

            String action = rule.action != null ? rule.action.toLowerCase() : "nop";
            switch (action) {
                case "nop":
                    nopMethod(method);
                    if (debug.logMethodRemovals) {
                        Log.info("No-oped method: " + targetClassName + "." +
                                method.name + method.desc);
                    }
                    break;

                case "remove":
                    it.remove();
                    if (debug.logMethodRemovals) {
                        Log.warn("Removed method: " + targetClassName + "." +
                                method.name + method.desc +
                                " (WARNING: may cause NoSuchMethodError if referenced)");
                    }
                    break;

                default:
                    Log.warn("Unknown action '" + action + "' for method " +
                            method.name + " in " + targetClassName);
            }
        }
    }

    private static boolean matchesMethod(MethodNode method,
                                         MixinHelperConfig.MethodRemovalRule rule,
                                         Pattern pattern) {
        // Match by exact method name
        if (rule.method != null && !rule.method.isEmpty()) {
            if (!method.name.equals(rule.method)) {
                return false;
            }
            // If descriptor is specified, it must also match
            if (rule.descriptor != null && !rule.descriptor.isEmpty()) {
                return method.desc.equals(rule.descriptor);
            }
            return true;
        }

        // Match by pattern
        if (pattern != null) {
            return pattern.matcher(method.name).matches();
        }

        return false;
    }

    /**
     * Replace a method's body with a minimal default-return implementation.
     * The method still exists and can be called, but it does nothing.
     */
    public static void nopMethod(MethodNode method) {
        method.instructions.clear();
        if (method.tryCatchBlocks != null) {
            method.tryCatchBlocks.clear();
        }
        method.localVariables = null;

        Type returnType = Type.getReturnType(method.desc);
        InsnList insns = new InsnList();

        switch (returnType.getSort()) {
            case Type.VOID:
                insns.add(new InsnNode(Opcodes.RETURN));
                break;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                insns.add(new InsnNode(Opcodes.ICONST_0));
                insns.add(new InsnNode(Opcodes.IRETURN));
                break;
            case Type.LONG:
                insns.add(new InsnNode(Opcodes.LCONST_0));
                insns.add(new InsnNode(Opcodes.LRETURN));
                break;
            case Type.FLOAT:
                insns.add(new InsnNode(Opcodes.FCONST_0));
                insns.add(new InsnNode(Opcodes.FRETURN));
                break;
            case Type.DOUBLE:
                insns.add(new InsnNode(Opcodes.DCONST_0));
                insns.add(new InsnNode(Opcodes.DRETURN));
                break;
            default: // OBJECT, ARRAY
                insns.add(new InsnNode(Opcodes.ACONST_NULL));
                insns.add(new InsnNode(Opcodes.ARETURN));
                break;
        }

        method.instructions = insns;
        method.maxStack = 2;
        // +1 for 'this' if instance method (non-static)
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        int paramSlots = 0;
        for (Type argType : Type.getArgumentTypes(method.desc)) {
            paramSlots += argType.getSize();
        }
        method.maxLocals = paramSlots + (isStatic ? 0 : 1);
    }
}
