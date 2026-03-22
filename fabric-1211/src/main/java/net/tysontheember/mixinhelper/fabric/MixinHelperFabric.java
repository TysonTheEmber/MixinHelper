package net.tysontheember.mixinhelper.fabric;

import net.fabricmc.api.ModInitializer;
import net.tysontheember.mixinhelper.MixinAuditLog;

public class MixinHelperFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        MixinAuditLog.finalizeReport();
    }
}
