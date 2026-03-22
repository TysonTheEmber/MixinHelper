package net.tysontheember.mixinhelper.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.tysontheember.mixinhelper.MixinAuditLog;
import net.tysontheember.mixinhelper.MixinHelperConstants;

@Mod(MixinHelperConstants.MOD_ID)
public class MixinHelperNeoForge {

    public MixinHelperNeoForge(IEventBus modEventBus) {
        MixinAuditLog.finalizeReport();
    }
}
