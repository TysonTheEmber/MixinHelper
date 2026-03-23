package net.tysontheember.mixinhelper;

import java.util.Arrays;
import java.util.List;

/**
 * Safety guardrails that prevent users from accidentally modifying classes
 * critical to the JVM, chunk generation, palette storage, or world persistence.
 * Operations targeting protected classes are blocked by default.
 */
public final class Guardrails {

    private Guardrails() {}

    public enum ProtectionCategory {
        CHUNK_GENERATION("Modifying chunk generation classes can corrupt world data"),
        PALETTE("Modifying palette classes can corrupt chunk data and crash the JVM"),
        MIXIN_SYSTEM("Modifying the mixin system itself will cause cascade failures"),
        CLASSLOADER("Modifying class loading will crash the JVM"),
        THREADING("Modifying threading classes can cause deadlocks"),
        WORLD_PERSISTENCE("Modifying save/load classes can corrupt world data"),
        USER_DEFINED("User-defined protected class pattern (see additionalProtectedPatterns in config)");

        private final String reason;

        ProtectionCategory(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    private static class ProtectedPattern {
        final String prefix;
        final ProtectionCategory category;

        ProtectedPattern(String prefix, ProtectionCategory category) {
            this.prefix = prefix;
            this.category = category;
        }
    }

    private static final List<ProtectedPattern> PROTECTED_PATTERNS = Arrays.asList(
            // Chunk generation
            new ProtectedPattern("net.minecraft.world.level.chunk.ChunkGenerator", ProtectionCategory.CHUNK_GENERATION),
            new ProtectedPattern("net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator", ProtectionCategory.CHUNK_GENERATION),
            new ProtectedPattern("net.minecraft.world.level.chunk.WorldGenRegion", ProtectionCategory.CHUNK_GENERATION),
            new ProtectedPattern("net.minecraft.world.level.chunk.LevelChunk", ProtectionCategory.CHUNK_GENERATION),
            new ProtectedPattern("net.minecraft.world.level.chunk.ChunkStatus", ProtectionCategory.CHUNK_GENERATION),

            // Palette containers
            new ProtectedPattern("net.minecraft.world.level.chunk.PalettedContainer", ProtectionCategory.PALETTE),
            new ProtectedPattern("net.minecraft.world.level.chunk.LinearPalette", ProtectionCategory.PALETTE),
            new ProtectedPattern("net.minecraft.world.level.chunk.HashMapPalette", ProtectionCategory.PALETTE),
            new ProtectedPattern("net.minecraft.world.level.chunk.Palette", ProtectionCategory.PALETTE),

            // Mixin system
            new ProtectedPattern("org.spongepowered.asm.", ProtectionCategory.MIXIN_SYSTEM),

            // ClassLoader / JVM internals
            new ProtectedPattern("java.lang.ClassLoader", ProtectionCategory.CLASSLOADER),
            new ProtectedPattern("sun.misc.Unsafe", ProtectionCategory.CLASSLOADER),
            new ProtectedPattern("jdk.internal.", ProtectionCategory.CLASSLOADER),

            // Threading
            new ProtectedPattern("net.minecraft.util.thread.BlockableEventLoop", ProtectionCategory.THREADING),
            new ProtectedPattern("net.minecraft.server.TickTask", ProtectionCategory.THREADING),

            // World persistence
            new ProtectedPattern("net.minecraft.world.level.storage.LevelStorageSource", ProtectionCategory.WORLD_PERSISTENCE),
            new ProtectedPattern("net.minecraft.nbt.", ProtectionCategory.WORLD_PERSISTENCE),
            new ProtectedPattern("net.minecraft.world.level.storage.LevelData", ProtectionCategory.WORLD_PERSISTENCE)
    );

    /**
     * Check if a class name matches any protected pattern.
     * Handles both dot-separated and slash-separated class names.
     *
     * @return the matching ProtectionCategory, or null if not protected
     */
    public static ProtectionCategory getProtectionCategory(String className,
                                                            MixinHelperConfig.GuardrailsConfig guardrails) {
        if (className == null || className.isEmpty()) return null;

        String normalized = className.replace('/', '.');

        // Check user-added additional patterns
        if (guardrails != null && guardrails.additionalProtectedPatterns != null) {
            for (String pattern : guardrails.additionalProtectedPatterns) {
                if (normalized.startsWith(pattern)) {
                    return ProtectionCategory.USER_DEFINED;
                }
            }
        }

        // Check hardcoded patterns
        for (ProtectedPattern entry : PROTECTED_PATTERNS) {
            if (normalized.startsWith(entry.prefix)) {
                // Check if user has explicitly excluded this class (requires bypass to also be on)
                if (guardrails != null
                        && guardrails.bypassProtectedClasses
                        && guardrails.excludeFromProtection != null
                        && guardrails.excludeFromProtection.contains(normalized)) {
                    return null;
                }
                return entry.category;
            }
        }

        return null;
    }

    /**
     * Check whether an operation on a target class should be allowed.
     *
     * @param className the target class being affected
     * @param operation human-readable description of the operation (for logging)
     * @param config    the full MixinHelperConfig
     * @return true if the operation should PROCEED, false if it should be BLOCKED
     */
    public static boolean checkTargetClass(String className, String operation,
                                           MixinHelperConfig config) {
        MixinHelperConfig.GuardrailsConfig guardrails = config.guardrails;

        // If guardrails are disabled entirely, allow everything
        if (guardrails != null && !guardrails.enabled) {
            return true;
        }

        ProtectionCategory category = getProtectionCategory(className, guardrails);
        if (category == null) {
            return true;
        }

        String normalized = className.replace('/', '.');

        Log.error("GUARDRAIL BLOCKED: " + operation);
        Log.error("  Class: " + normalized);
        Log.error("  Category: " + category.name());
        Log.error("  Reason: " + category.getReason());

        // Check if bypass is enabled
        if (guardrails != null && guardrails.bypassProtectedClasses) {
            Log.warn("GUARDRAIL BYPASSED: bypassProtectedClasses is enabled. "
                    + "Proceeding with " + operation + " on " + normalized);
            Log.warn("  YOU ARE ACCEPTING THE RISK OF: " + category.getReason());
            return true;
        }

        Log.error("  To override, set guardrails.bypassProtectedClasses = true in "
                + MixinHelperConstants.CONFIG_FILE);
        Log.error("  THIS IS DANGEROUS AND MAY CORRUPT YOUR WORLD OR CRASH THE GAME.");
        return false;
    }

    /**
     * Validate the entire config at load time and log all warnings upfront.
     * Non-blocking — only warns. Actual blocking happens at execution time.
     *
     * @return the count of problematic entries found
     */
    public static int validateConfig(MixinHelperConfig config) {
        if (config.guardrails != null && !config.guardrails.enabled) {
            Log.warn("Guardrails are DISABLED. No protection against dangerous class modifications.");
            return 0;
        }

        int issues = 0;
        MixinHelperConfig.GuardrailsConfig guardrails = config.guardrails;

        // Check blacklist.targetClasses
        if (config.blacklist != null && config.blacklist.targetClasses != null) {
            for (String targetClass : config.blacklist.targetClasses) {
                ProtectionCategory cat = getProtectionCategory(targetClass, guardrails);
                if (cat != null) {
                    issues++;
                    Log.warn("CONFIG WARNING: blacklist.targetClasses contains protected class '"
                            + targetClass + "' (" + cat.name() + ": " + cat.getReason() + ")");
                }
            }
        }

        // Check methodRemovals rules
        if (config.methodRemovals != null && config.methodRemovals.rules != null) {
            for (MixinHelperConfig.MethodRemovalRule rule : config.methodRemovals.rules) {
                if (rule.targetClass != null) {
                    ProtectionCategory cat = getProtectionCategory(rule.targetClass, guardrails);
                    if (cat != null) {
                        issues++;
                        Log.warn("CONFIG WARNING: methodRemovals rule targets protected class '"
                                + rule.targetClass + "' (" + cat.name() + ": " + cat.getReason() + ")");
                    }
                }
            }
        }

        if (issues > 0) {
            if (guardrails != null && guardrails.bypassProtectedClasses) {
                Log.warn("Found " + issues + " guardrail issue(s). "
                        + "bypassProtectedClasses is ON — these operations will proceed at your risk.");
            } else {
                Log.error("Found " + issues + " guardrail issue(s). "
                        + "These operations will be BLOCKED at runtime. "
                        + "Set guardrails.bypassProtectedClasses = true to override.");
            }
        }

        return issues;
    }
}
