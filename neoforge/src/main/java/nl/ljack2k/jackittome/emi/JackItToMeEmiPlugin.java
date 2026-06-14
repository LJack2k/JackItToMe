package nl.ljack2k.jackittome.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/**
 * EMI plugin entry point. Registered via {@code @EmiEntrypoint} (scanned by
 * EMI on NeoForge) and duplicated in the Java SPI file for safety.
 * <p>
 * Registers a generic {@link JackEmiRecipeDecorator} that adds the pull button
 * to every recipe display — the EMI equivalent of JEI's universal button
 * factory.
 */
@EmiEntrypoint
public final class JackItToMeEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipeDecorator(new JackEmiRecipeDecorator());
    }
}
