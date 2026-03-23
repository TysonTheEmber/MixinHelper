package net.tysontheember.mixinhelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MixinHelperConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static MixinHelperConfig INSTANCE;

    @SerializedName("_WARNING")
    public String warning = "USE AT YOUR OWN RISK. Incorrect use can cause crashes, broken mods, and world corruption. Back up your worlds. Do NOT report bugs to mod authors if you have modified their mixins with this tool.";

    @SerializedName("enabled")
    public boolean enabled = true;

    @SerializedName("blacklist")
    public BlacklistConfig blacklist = new BlacklistConfig();

    @SerializedName("priorities")
    public PriorityConfig priorities = new PriorityConfig();

    @SerializedName("methodRemovals")
    public MethodRemovalConfig methodRemovals = new MethodRemovalConfig();

    @SerializedName("audit")
    public AuditConfig audit = new AuditConfig();

    @SerializedName("debug")
    public DebugConfig debug = new DebugConfig();

    @SerializedName("guardrails")
    public GuardrailsConfig guardrails = new GuardrailsConfig();

    public static class BlacklistConfig {
        @SerializedName("mixins")
        public List<String> mixins = new ArrayList<>();

        @SerializedName("mixinConfigs")
        public List<String> mixinConfigs = new ArrayList<>();

        @SerializedName("targetClasses")
        public List<String> targetClasses = new ArrayList<>();
    }

    public static class PriorityConfig {
        @SerializedName("mixinConfigPriorities")
        public Map<String, Integer> mixinConfigPriorities = new HashMap<>();

        @SerializedName("mixinPriorities")
        public Map<String, Integer> mixinPriorities = new HashMap<>();
    }

    public static class MethodRemovalConfig {
        @SerializedName("rules")
        public List<MethodRemovalRule> rules = new ArrayList<>();
    }

    public static class MethodRemovalRule {
        @SerializedName("targetClass")
        public String targetClass;

        @SerializedName("method")
        public String method;

        @SerializedName("methodPattern")
        public String methodPattern;

        @SerializedName("descriptor")
        public String descriptor;

        @SerializedName("action")
        public String action = "nop";
    }

    public static class AuditConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        @SerializedName("outputFile")
        public String outputFile = "config/mixinhelper-report.json";

        @SerializedName("includeAnnotations")
        public boolean includeAnnotations = true;
    }

    public static class DebugConfig {
        @SerializedName("verbose")
        public boolean verbose = false;

        @SerializedName("logBlacklistActions")
        public boolean logBlacklistActions = true;

        @SerializedName("logMethodRemovals")
        public boolean logMethodRemovals = true;
    }

    public static class GuardrailsConfig {
        @SerializedName("enabled")
        public boolean enabled = true;

        @SerializedName("bypassProtectedClasses")
        public boolean bypassProtectedClasses = false;

        @SerializedName("additionalProtectedPatterns")
        public List<String> additionalProtectedPatterns = new ArrayList<>();

        @SerializedName("excludeFromProtection")
        public List<String> excludeFromProtection = new ArrayList<>();
    }

    public static MixinHelperConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            MixinHelperConfig defaults = new MixinHelperConfig();
            generateDefault(configPath, defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            MixinHelperConfig config = GSON.fromJson(reader, MixinHelperConfig.class);
            if (config == null) {
                Log.warn("Config file was empty, using defaults.");
                return new MixinHelperConfig();
            }
            // Null-guard all fields in case of partial JSON
            if (config.blacklist == null) config.blacklist = new BlacklistConfig();
            if (config.priorities == null) config.priorities = new PriorityConfig();
            if (config.methodRemovals == null) config.methodRemovals = new MethodRemovalConfig();
            if (config.audit == null) config.audit = new AuditConfig();
            if (config.debug == null) config.debug = new DebugConfig();
            if (config.blacklist.mixins == null) config.blacklist.mixins = new ArrayList<>();
            if (config.blacklist.mixinConfigs == null) config.blacklist.mixinConfigs = new ArrayList<>();
            if (config.blacklist.targetClasses == null) config.blacklist.targetClasses = new ArrayList<>();
            if (config.priorities.mixinConfigPriorities == null) config.priorities.mixinConfigPriorities = new HashMap<>();
            if (config.priorities.mixinPriorities == null) config.priorities.mixinPriorities = new HashMap<>();
            if (config.methodRemovals.rules == null) config.methodRemovals.rules = new ArrayList<>();
            if (config.guardrails == null) config.guardrails = new GuardrailsConfig();
            if (config.guardrails.additionalProtectedPatterns == null) config.guardrails.additionalProtectedPatterns = new ArrayList<>();
            if (config.guardrails.excludeFromProtection == null) config.guardrails.excludeFromProtection = new ArrayList<>();
            Log.info("Loaded config from " + configPath);
            return config;
        } catch (JsonSyntaxException e) {
            Log.error("Failed to parse config: " + e.getMessage());
            Log.error("Using default configuration.");
            return new MixinHelperConfig();
        } catch (IOException e) {
            Log.error("Failed to read config file: " + e.getMessage());
            return new MixinHelperConfig();
        }
    }

    private static void generateDefault(Path configPath, MixinHelperConfig defaults) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(defaults, writer);
            }
            Log.info("Generated default config at " + configPath);
        } catch (IOException e) {
            Log.error("Failed to generate default config: " + e.getMessage());
        }
    }

    public static Path findConfigPath() {
        Path gameDir = findGameDirectory();
        return gameDir.resolve("config").resolve(MixinHelperConstants.CONFIG_FILE);
    }

    static Path findGameDirectory() {
        // Fabric
        String fabricDir = System.getProperty("fabric.gameDir");
        if (fabricDir != null) return Path.of(fabricDir);

        // NeoForge
        String neoDir = System.getProperty("neoforge.gameDir");
        if (neoDir != null) return Path.of(neoDir);

        // Forge (FML sets user.dir or we check common properties)
        String userDir = System.getProperty("user.dir");
        if (userDir != null) return Path.of(userDir);

        // Fallback
        return Path.of("").toAbsolutePath();
    }
}
