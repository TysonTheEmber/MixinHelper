package net.tysontheember.mixinhelper;

public final class MixinHelperConstants {

    public static final String MOD_ID = "mixinhelper";
    public static final String MOD_NAME = "Mixin Helper";
    public static final String CONFIG_FILE = "mixinhelper.json";
    public static final String REPORT_FILE = "mixinhelper-report.json";
    public static final String LOG_PREFIX = "[MixinHelper]";

    public static final String[] STARTUP_WARNING = {
            "========================================================",
            "  WARNING: MIXIN HELPER — USE AT YOUR OWN RISK",
            "  This mod directly interferes with how other mods",
            "  patch the game. Incorrect use WILL break things.",
            "",
            "  Possible consequences:",
            "  - Game crashes with difficult-to-diagnose stacktraces",
            "  - Broken mod features that may not be obvious",
            "  - World corruption — BACK UP YOUR WORLDS",
            "  - Incompatible save files if disabled mixins affect",
            "    world data",
            "",
            "  Do NOT report bugs to mod authors if you have",
            "  modified their mixins with this tool.",
            "========================================================"
    };

    private MixinHelperConstants() {}
}
