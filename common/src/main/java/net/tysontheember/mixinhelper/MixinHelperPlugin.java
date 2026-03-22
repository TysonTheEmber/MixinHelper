package net.tysontheember.mixinhelper;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The core IMixinConfigPlugin for Mixin Helper.
 * This plugin class is referenced in mixinhelper.mixins.json and serves as the
 * bootstrap entry point for the entire mod.
 *
 * Lifecycle:
 *   onLoad()        — config loaded, interceptor stored for later
 *   acceptTargets() — all configs are registered; run blacklist + audit here
 *   finalizeReport() — called from mod entrypoint; write report to disk
 */
public class MixinHelperPlugin implements IMixinConfigPlugin {

    private static MixinInterceptor interceptor;

    @Override
    public void onLoad(String mixinPackage) {
        Log.info("Initializing Mixin Helper...");

        // 1. Load config from disk
        Path configPath = MixinHelperConfig.findConfigPath();
        MixinHelperConfig config = MixinHelperConfig.load(configPath);
        MixinHelperConfig.INSTANCE = config;

        if (!config.enabled) {
            Log.info("Mixin Helper is disabled via config.");
            return;
        }

        // 2. Create interceptor (but don't execute yet — other configs aren't registered)
        interceptor = new MixinInterceptor(config);

        // 3. Create audit log instance
        if (config.audit.enabled) {
            MixinAuditLog.INSTANCE = new MixinAuditLog(config);
        }

        Log.info("Config loaded. Waiting for mixin configs to be registered...");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // This is called AFTER all configs are registered and initialized.
        // Now is the right time to enumerate configs, apply blacklisting, and record audit data.
        MixinHelperConfig config = MixinHelperConfig.INSTANCE;
        if (config == null || !config.enabled) return;

        Log.info("All mixin configs registered. Applying blacklist and recording audit...");

        // Record audit BEFORE blacklisting (captures full original state)
        if (MixinAuditLog.INSTANCE != null) {
            MixinAuditLog.INSTANCE.recordAllConfigs();
        }

        // Apply blacklist and priority overrides
        if (interceptor != null) {
            interceptor.execute();

            // Record blacklisted entries in audit log
            if (MixinAuditLog.INSTANCE != null) {
                for (String mixin : interceptor.getBlacklistedMixins()) {
                    MixinAuditLog.INSTANCE.recordBlacklisted(mixin, "Blacklisted via config");
                }
                for (String configName : interceptor.getBlacklistedConfigs()) {
                    MixinAuditLog.INSTANCE.recordBlacklisted(configName, "Entire config blacklisted");
                }
            }
        }

        Log.info("Mixin Helper initialization complete.");
    }

    @Override
    public List<String> getMixins() {
        return Collections.emptyList();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
