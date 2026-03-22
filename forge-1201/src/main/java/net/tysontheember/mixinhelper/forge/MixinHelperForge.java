package net.tysontheember.mixinhelper.forge;

import net.minecraftforge.fml.common.Mod;
import net.tysontheember.mixinhelper.MixinAuditLog;
import net.tysontheember.mixinhelper.MixinHelperConstants;

@Mod(MixinHelperConstants.MOD_ID)
public class MixinHelperForge {

    public MixinHelperForge() {
        MixinAuditLog.finalizeReport();
    }
}
