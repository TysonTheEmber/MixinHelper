package net.tysontheember.mixinhelper;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Wraps another mod's IMixinConfigPlugin to intercept postApply for method
 * stripping and shouldApplyMixin for target-class blacklisting.
 * Delegates all other calls to the original plugin.
 */
public class PluginWrapper implements IMixinConfigPlugin {

    private final IMixinConfigPlugin delegate;
    private final MixinHelperConfig config;
    private final String configName;

    public PluginWrapper(IMixinConfigPlugin delegate, MixinHelperConfig config, String configName) {
        this.delegate = delegate;
        this.config = config;
        this.configName = configName;
    }

    @Override
    public void onLoad(String mixinPackage) {
        if (delegate != null) {
            delegate.onLoad(mixinPackage);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return delegate != null ? delegate.getRefMapperConfig() : null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Target-class blacklisting
        if (config.blacklist.targetClasses.contains(targetClassName)) {
            if (config.debug.logBlacklistActions) {
                Log.info("Blocked mixin " + mixinClassName + " targeting blacklisted class: " + targetClassName);
            }
            return false;
        }

        // Check if this specific mixin is blacklisted (catches any that weren't
        // removed from the list during the reflection phase)
        if (config.blacklist.mixins.contains(mixinClassName)) {
            if (config.debug.logBlacklistActions) {
                Log.info("Blocked blacklisted mixin: " + mixinClassName);
            }
            return false;
        }

        // Delegate to original plugin
        if (delegate != null) {
            return delegate.shouldApplyMixin(targetClassName, mixinClassName);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        if (delegate != null) {
            delegate.acceptTargets(myTargets, otherTargets);
        }
    }

    @Override
    public List<String> getMixins() {
        return delegate != null ? delegate.getMixins() : null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        if (delegate != null) {
            delegate.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        // Delegate first
        if (delegate != null) {
            delegate.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
        }

        // Apply method stripping rules
        MethodStripper.processClassNode(targetClass, targetClassName, config);
    }
}
