package net.tysontheember.mixinhelper;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

import java.util.*;

/**
 * Core engine that blacklists other mods' mixins and overrides priorities
 * by reflecting into Mixin internals during the acceptTargets() window.
 */
public class MixinInterceptor {

    private final MixinHelperConfig config;
    private final List<String> blacklistedMixins = new ArrayList<>();
    private final List<String> blacklistedConfigs = new ArrayList<>();

    public MixinInterceptor(MixinHelperConfig config) {
        this.config = config;
    }

    public void execute() {
        Collection<Object> configs = ReflectionHelper.getAllMixinConfigs();

        if (configs.isEmpty()) {
            Log.info("No mixin configs found to process.");
            return;
        }

        Log.warn("Applying mixin modifications — if you experience issues, disable Mixin Helper and test without it before reporting bugs to other mod authors.");
        Log.info("Found " + configs.size() + " mixin config(s) for blacklist/priority processing.");

        for (Object configWrapper : configs) {
            String configName = getConfigName(configWrapper);

            // Skip our own config
            if ("mixinhelper.mixins.json".equals(configName)) {
                continue;
            }

            processConfig(configWrapper, configName);
        }

        if (!blacklistedMixins.isEmpty()) {
            Log.info("Blacklisted " + blacklistedMixins.size() + " mixin(s) total.");
        }
        if (!blacklistedConfigs.isEmpty()) {
            Log.info("Blacklisted " + blacklistedConfigs.size() + " entire config(s).");
        }
    }

    private String getConfigName(Object configWrapper) {
        Object name = ReflectionHelper.invokeMethod(configWrapper, configWrapper.getClass(), "getName");
        return name != null ? name.toString() : configWrapper.toString();
    }

    private void processConfig(Object configWrapper, String configName) {
        Object mixinConfig = getMixinConfig(configWrapper);
        if (mixinConfig == null) {
            Log.warn("Could not access internals of config: " + configName);
            return;
        }

        // Check if entire config is blacklisted
        if (config.blacklist.mixinConfigs.contains(configName)) {
            clearAllMixins(mixinConfig, configName);
            blacklistedConfigs.add(configName);
            if (config.debug.logBlacklistActions) {
                Log.info("Blacklisted entire config: " + configName);
            }
            return;
        }

        // Apply priority overrides
        applyPriorityOverride(mixinConfig, configName);

        // Blacklist individual mixins from the prepared MixinInfo lists
        blacklistIndividualMixins(mixinConfig, configName);

        // Install plugin wrapper for method removal if needed
        installPluginWrapperIfNeeded(mixinConfig, configName);
    }

    private Object getMixinConfig(Object configWrapper) {
        Object result = ReflectionHelper.invokeMethod(configWrapper, configWrapper.getClass(), "get");
        if (result != null) return result;
        return ReflectionHelper.getFieldValue(configWrapper, configWrapper.getClass(), "config");
    }

    /**
     * Clears ALL prepared mixin data from a config.
     * By acceptTargets() time, the string lists (mixinClasses etc.) have already been
     * consumed into MixinInfo objects. We must clear the actual runtime lists:
     * - mixins (List<MixinInfo>) — all prepared mixins
     * - pendingMixins (List<MixinInfo>) — mixins waiting to be applied
     * - mixinMapping (Map<String, List<MixinInfo>>) — target class to mixin mapping
     */
    @SuppressWarnings("unchecked")
    private void clearAllMixins(Object mixinConfig, String configName) {
        // Clear the prepared MixinInfo lists (the actual runtime data)
        for (String fieldName : new String[]{"mixins", "pendingMixins"}) {
            List<?> list = (List<?>) ReflectionHelper.getFieldValue(
                    mixinConfig, mixinConfig.getClass(), fieldName);
            if (list != null) {
                int count = list.size();
                list.clear();
                if (count > 0) {
                    Log.debug("Cleared " + count + " entries from " + configName + "." + fieldName);
                }
            }
        }

        // Clear the target class -> mixin mapping
        Map<?, ?> mapping = (Map<?, ?>) ReflectionHelper.getFieldValue(
                mixinConfig, mixinConfig.getClass(), "mixinMapping");
        if (mapping != null) {
            mapping.clear();
        }

        // Also clear the string lists in case they haven't been consumed yet
        for (String fieldName : new String[]{"mixinClasses", "mixinClassesClient", "mixinClassesServer"}) {
            List<String> list = (List<String>) ReflectionHelper.getFieldValue(
                    mixinConfig, mixinConfig.getClass(), fieldName);
            if (list != null) {
                list.clear();
            }
        }
    }

    private void applyPriorityOverride(Object mixinConfig, String configName) {
        Integer newPriority = config.priorities.mixinConfigPriorities.get(configName);
        if (newPriority != null) {
            boolean success = ReflectionHelper.setFieldValue(
                    mixinConfig, mixinConfig.getClass(), "priority", newPriority);
            if (success) {
                Log.info("Set priority of " + configName + " to " + newPriority);
            }
        }
    }

    /**
     * Removes individual blacklisted mixins from the prepared MixinInfo lists.
     * Uses MixinInfo.getClassName() to match against the blacklist.
     */
    @SuppressWarnings("unchecked")
    private void blacklistIndividualMixins(Object mixinConfig, String configName) {
        if (config.blacklist.mixins.isEmpty()) return;

        // Remove from mixins and pendingMixins lists
        for (String fieldName : new String[]{"mixins", "pendingMixins"}) {
            List<?> mixinList = (List<?>) ReflectionHelper.getFieldValue(
                    mixinConfig, mixinConfig.getClass(), fieldName);
            if (mixinList == null || mixinList.isEmpty()) continue;

            Iterator<?> it = mixinList.iterator();
            while (it.hasNext()) {
                Object mixinInfo = it.next();
                String className = getMixinInfoClassName(mixinInfo);
                if (className != null && config.blacklist.mixins.contains(className)) {
                    it.remove();
                    blacklistedMixins.add(className);
                    if (config.debug.logBlacklistActions) {
                        Log.info("Blacklisted mixin: " + className + " from " + configName);
                    }
                }
            }
        }

        // Also remove from mixinMapping values
        Map<String, List<?>> mapping = (Map<String, List<?>>) ReflectionHelper.getFieldValue(
                mixinConfig, mixinConfig.getClass(), "mixinMapping");
        if (mapping != null) {
            for (List<?> targetMixins : mapping.values()) {
                Iterator<?> it = targetMixins.iterator();
                while (it.hasNext()) {
                    Object mixinInfo = it.next();
                    String className = getMixinInfoClassName(mixinInfo);
                    if (className != null && config.blacklist.mixins.contains(className)) {
                        it.remove();
                    }
                }
            }
        }

        // Also clear from string lists in case they haven't been consumed
        String mixinPackage = getMixinPackage(mixinConfig);
        for (String fieldName : new String[]{"mixinClasses", "mixinClassesClient", "mixinClassesServer"}) {
            List<String> stringList = (List<String>) ReflectionHelper.getFieldValue(
                    mixinConfig, mixinConfig.getClass(), fieldName);
            if (stringList == null || stringList.isEmpty()) continue;

            Iterator<String> it = stringList.iterator();
            while (it.hasNext()) {
                String shortName = it.next();
                String fullName = mixinPackage.isEmpty() ? shortName : mixinPackage + "." + shortName;
                if (config.blacklist.mixins.contains(fullName)) {
                    it.remove();
                }
            }
        }
    }

    private String getMixinPackage(Object mixinConfig) {
        Object pkg = ReflectionHelper.getFieldValue(mixinConfig, mixinConfig.getClass(), "mixinPackage");
        return pkg != null ? pkg.toString() : "";
    }

    private String getMixinInfoClassName(Object mixinInfo) {
        Object name = ReflectionHelper.invokeMethod(mixinInfo, mixinInfo.getClass(), "getClassName");
        return name != null ? name.toString() : null;
    }

    private void installPluginWrapperIfNeeded(Object mixinConfig, String configName) {
        boolean needsWrapper = !config.methodRemovals.rules.isEmpty()
                || !config.blacklist.targetClasses.isEmpty();

        if (!needsWrapper) return;

        try {
            // MixinConfig.plugin is of type PluginHandle (not IMixinConfigPlugin directly).
            // PluginHandle has a 'plugin' field of type IMixinConfigPlugin.
            Object pluginHandle = ReflectionHelper.getFieldValue(
                    mixinConfig, mixinConfig.getClass(), "plugin");

            if (pluginHandle == null) {
                Log.debug("No plugin handle on config " + configName + ", skipping wrapper.");
                return;
            }

            // Get the actual IMixinConfigPlugin from inside the PluginHandle
            IMixinConfigPlugin existingPlugin = null;
            Object innerPlugin = ReflectionHelper.getFieldValue(
                    pluginHandle, pluginHandle.getClass(), "plugin");
            if (innerPlugin instanceof IMixinConfigPlugin) {
                existingPlugin = (IMixinConfigPlugin) innerPlugin;
            }

            // Replace the inner plugin with our wrapper
            PluginWrapper wrapper = new PluginWrapper(existingPlugin, config, configName);
            boolean success = ReflectionHelper.setFieldValue(
                    pluginHandle, pluginHandle.getClass(), "plugin", wrapper);

            if (success) {
                Log.debug("Installed plugin wrapper on config: " + configName);
            }
        } catch (Exception e) {
            Log.warn("Failed to install plugin wrapper on " + configName + ": " + e.getMessage());
        }
    }

    public List<String> getBlacklistedMixins() {
        return Collections.unmodifiableList(blacklistedMixins);
    }

    public List<String> getBlacklistedConfigs() {
        return Collections.unmodifiableList(blacklistedConfigs);
    }
}
