package nl.ljack2k.jackittome.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side config. Deliberately tiny — the mod is zero-config by design, and
 * every option here exists to give players an escape hatch when a third-party
 * viewer changes under us.
 */
public final class JackConfig {
    private JackConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_RECIPE_BUTTON;
    public static final ModConfigSpec.IntValue BUTTON_OFFSET_X;
    public static final ModConfigSpec.IntValue BUTTON_OFFSET_Y;
    public static final ModConfigSpec.BooleanValue PLACEMENT_KEYS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        SHOW_RECIPE_BUTTON = b
                .comment("Show the jack button on recipes, in whichever recipe viewer you use.",
                        "",
                        "Turn this off if you would rather pull only with the keybinds. The rest of",
                        "the mod — the G keys, autocrafting, the hover tooltip — is unaffected.",
                        "",
                        "Note the button already hides itself when there is nothing to pull from,",
                        "i.e. when you open a recipe with only your own inventory behind it.")
                .translation("jackittome.configuration.showRecipeButton")
                .define("showRecipeButton", true);

        BUTTON_OFFSET_X = b
                .comment("EMI and REI only. Horizontal nudge for the recipe button, in GUI pixels.",
                        "Positive moves it right. Zero is the default spot: its own column beside",
                        "the recipe, vertically centred.",
                        "",
                        "JEI is not affected: it decides where its own recipe buttons go.")
                .translation("jackittome.configuration.buttonOffsetX")
                .defineInRange("buttonOffsetX", 0, -128, 128);

        BUTTON_OFFSET_Y = b
                .comment("EMI and REI only. Vertical nudge for the recipe button, in GUI pixels.",
                        "Positive moves it down.")
                .translation("jackittome.configuration.buttonOffsetY")
                .defineInRange("buttonOffsetY", 0, -128, 128);

        PLACEMENT_KEYS = b
                .comment("EMI and REI only. Opt-in: allow Ctrl + arrow keys to reposition the recipe",
                        "button while a recipe screen is open (Ctrl+Home resets it). Each nudge is",
                        "saved to buttonOffsetX/Y above, so you can switch this back off once the",
                        "position looks right.",
                        "",
                        "Off by default so the key combination stays free for everyone happy with the",
                        "default placement. Ignored while the recipe viewer's search box has focus, so",
                        "it never eats Ctrl+Left/Right word-jump while you are typing.")
                .translation("jackittome.configuration.placementKeys")
                .define("placementKeys", false);

        SPEC = b.build();
    }
}
