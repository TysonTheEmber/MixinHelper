package net.tysontheember.mixinhelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Collects information about all loaded mixin configs and their mixins,
 * then writes a detailed audit report to a JSON file.
 */
public class MixinAuditLog {

    public static MixinAuditLog INSTANCE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MixinHelperConfig config;
    private final List<ConfigEntry> configEntries = new ArrayList<>();
    private final List<BlacklistEntry> blacklistEntries = new ArrayList<>();
    private boolean finalized = false;
    private boolean configsRecorded = false;

    public MixinAuditLog(MixinHelperConfig config) {
        this.config = config;
    }

    /**
     * Enumerate all registered mixin configs via Mixins.getConfigs().
     * Uses reflection to avoid compile-time dependency on the internal Config class.
     */
    public void recordAllConfigs() {
        try {
            Collection<Object> configs = ReflectionHelper.getAllMixinConfigs();
            Log.info("Audit: found " + configs.size() + " mixin config(s) in registry.");

            for (Object configWrapper : configs) {
                try {
                    recordConfig(configWrapper);
                } catch (Exception e) {
                    Log.warn("Failed to record config: " + e.getMessage());
                }
            }

            if (!configEntries.isEmpty()) {
                configsRecorded = true;
            }

            Log.info("Audit: recorded " + configEntries.size() + " config(s) with " +
                    configEntries.stream().mapToInt(c -> c.mixins.size()).sum() + " total mixin(s).");
        } catch (Exception e) {
            Log.error("Failed to enumerate mixin configs: " + e.getMessage());
            if (config.debug.verbose) {
                Log.error("Stack trace:", e);
            }
        }
    }

    private void recordConfig(Object configWrapper) {
        ConfigEntry entry = new ConfigEntry();

        // Get config name via getName() (public method on Config)
        Object name = ReflectionHelper.invokeMethod(configWrapper, configWrapper.getClass(), "getName");
        entry.name = name != null ? name.toString() : configWrapper.toString();

        // Access the underlying MixinConfig via get() (package-private)
        Object mixinConfig = ReflectionHelper.invokeMethod(configWrapper, configWrapper.getClass(), "get");
        if (mixinConfig == null) {
            // Try field access as fallback
            mixinConfig = ReflectionHelper.getFieldValue(configWrapper, configWrapper.getClass(), "config");
        }

        if (mixinConfig != null) {
            Object pkg = ReflectionHelper.getFieldValue(mixinConfig, mixinConfig.getClass(), "mixinPackage");
            entry.packageName = pkg != null ? pkg.toString() : "";

            Object priority = ReflectionHelper.getFieldValue(mixinConfig, mixinConfig.getClass(), "priority");
            entry.priority = priority instanceof Integer ? (Integer) priority : 1000;

            Object pluginClassName = ReflectionHelper.getFieldValue(mixinConfig, mixinConfig.getClass(), "pluginClassName");
            entry.pluginClass = pluginClassName != null ? pluginClassName.toString() : null;

            // Collect mixins from all three lists
            collectMixins(entry, mixinConfig, "mixinClasses", "common");
            collectMixins(entry, mixinConfig, "mixinClassesClient", "client");
            collectMixins(entry, mixinConfig, "mixinClassesServer", "server");
        } else {
            Log.warn("Audit: Could not access internals of config '" + entry.name + "'");
        }

        configEntries.add(entry);
    }

    @SuppressWarnings("unchecked")
    private void collectMixins(ConfigEntry entry, Object mixinConfig,
                               String fieldName, String side) {
        Object rawList = ReflectionHelper.getFieldValue(mixinConfig, mixinConfig.getClass(), fieldName);
        if (rawList == null) {
            // Try alternate field names (some Mixin versions use different names)
            if ("mixinClasses".equals(fieldName)) {
                rawList = ReflectionHelper.getFieldValue(mixinConfig, mixinConfig.getClass(), "mixins");
            } else if ("mixinClassesClient".equals(fieldName)) {
                rawList = ReflectionHelper.getFieldValue(mixinConfig, mixinConfig.getClass(), "client");
            } else if ("mixinClassesServer".equals(fieldName)) {
                rawList = ReflectionHelper.getFieldValue(mixinConfig, mixinConfig.getClass(), "server");
            }
        }
        if (!(rawList instanceof List)) return;

        List<String> mixinList = (List<String>) rawList;

        for (String shortName : mixinList) {
            MixinEntry mixin = new MixinEntry();
            String fullName = entry.packageName.isEmpty() ? shortName : entry.packageName + "." + shortName;
            mixin.className = fullName;
            mixin.side = side;
            mixin.blacklisted = false;

            // Scan annotations if enabled
            if (config.audit.includeAnnotations) {
                mixin.annotations = scanAnnotations(fullName);
            }

            entry.mixins.add(mixin);
        }
    }

    private List<String> scanAnnotations(String className) {
        List<String> annotations = new ArrayList<>();
        String resourcePath = className.replace('.', '/') + ".class";

        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                try (InputStream is2 = ClassLoader.getSystemResourceAsStream(resourcePath)) {
                    if (is2 != null) {
                        return parseAnnotations(is2);
                    }
                }
                // Try the classloader that loaded MixinAuditLog itself
                try (InputStream is3 = MixinAuditLog.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is3 != null) {
                        return parseAnnotations(is3);
                    }
                }
                return annotations;
            }
            return parseAnnotations(is);
        } catch (Exception e) {
            Log.debug("Could not scan annotations for " + className + ": " + e.getMessage());
            return annotations;
        }
    }

    private List<String> parseAnnotations(InputStream is) throws IOException {
        Set<String> annotations = new LinkedHashSet<>();
        ClassReader reader = new ClassReader(is);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

        for (MethodNode method : classNode.methods) {
            if (method.visibleAnnotations != null) {
                for (AnnotationNode ann : method.visibleAnnotations) {
                    String name = extractAnnotationName(ann.desc);
                    if (name != null && isMixinAnnotation(name)) {
                        annotations.add("@" + name);
                    }
                }
            }
            if (method.invisibleAnnotations != null) {
                for (AnnotationNode ann : method.invisibleAnnotations) {
                    String name = extractAnnotationName(ann.desc);
                    if (name != null && isMixinAnnotation(name)) {
                        annotations.add("@" + name);
                    }
                }
            }
        }

        return new ArrayList<>(annotations);
    }

    private String extractAnnotationName(String desc) {
        if (desc == null || !desc.startsWith("L") || !desc.endsWith(";")) {
            return null;
        }
        String internal = desc.substring(1, desc.length() - 1);
        int lastSlash = internal.lastIndexOf('/');
        return lastSlash >= 0 ? internal.substring(lastSlash + 1) : internal;
    }

    private boolean isMixinAnnotation(String name) {
        switch (name) {
            case "Inject":
            case "Redirect":
            case "Overwrite":
            case "ModifyArg":
            case "ModifyArgs":
            case "ModifyVariable":
            case "ModifyConstant":
            case "Accessor":
            case "Invoker":
            case "ModifyExpressionValue":
            case "ModifyReturnValue":
            case "ModifyReceiver":
            case "WrapOperation":
            case "WrapWithCondition":
                return true;
            default:
                return false;
        }
    }

    public void recordBlacklisted(String className, String reason) {
        BlacklistEntry entry = new BlacklistEntry();
        entry.className = className;
        entry.reason = reason;
        blacklistEntries.add(entry);
    }

    public static void finalizeReport() {
        if (INSTANCE == null || INSTANCE.finalized) return;
        INSTANCE.finalized = true;

        // If configs weren't recorded yet (e.g., timing issue), try again now
        if (!INSTANCE.configsRecorded) {
            Log.info("Attempting late config enumeration at finalization time...");
            INSTANCE.recordAllConfigs();
        }

        INSTANCE.writeReport();
    }

    private void writeReport() {
        if (!config.audit.enabled) return;

        Path gameDir = MixinHelperConfig.findGameDirectory();
        Path outputPath = gameDir.resolve(config.audit.outputFile);

        AuditReport report = new AuditReport();
        report.generatedAt = Instant.now().toString();
        report.totalMixinConfigs = configEntries.size();
        report.totalMixins = configEntries.stream().mapToInt(c -> c.mixins.size()).sum();
        report.blacklistedCount = blacklistEntries.size();
        report.mixinConfigs = configEntries;
        report.blacklistedMixins = blacklistEntries;

        try {
            Files.createDirectories(outputPath.getParent());
            try (Writer writer = Files.newBufferedWriter(outputPath)) {
                GSON.toJson(report, writer);
            }
            Log.info("Audit report written to " + outputPath +
                    " (" + report.totalMixinConfigs + " mixin configs, " + report.totalMixins + " mixins)");
        } catch (IOException e) {
            Log.error("Failed to write audit report: " + e.getMessage());
        }
    }

    // Data classes for JSON serialization

    static class AuditReport {
        String generatedAt;
        int totalMixinConfigs;
        int totalMixins;
        int blacklistedCount;
        List<ConfigEntry> mixinConfigs;
        List<BlacklistEntry> blacklistedMixins;
    }

    static class ConfigEntry {
        String name;
        String packageName;
        int priority;
        String pluginClass;
        List<MixinEntry> mixins = new ArrayList<>();
    }

    static class MixinEntry {
        String className;
        List<String> annotations;
        String side;
        boolean blacklisted;
    }

    static class BlacklistEntry {
        String className;
        String reason;
    }
}
