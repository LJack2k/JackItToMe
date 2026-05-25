package nl.ljack2k.jackittome.network;

import nl.ljack2k.jackittome.JackItToMe;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Centralized network registration. Called from the main mod constructor.
 */
public final class ModPackets {
    private ModPackets() {}

    public static void register(final RegisterPayloadHandlersEvent event) {
        // Channel version bumped on every wire-format break. Mismatched
        // versions reject cleanly at handshake instead of silently failing
        // to decode mid-game.
        //   1: initial release
        //   2: AvailabilityResponsePayload gained craftable[]
        //   3: OpenRsAutocraftPayload gained amount, plus chain stacks now
        //      carry shortfall in their count field
        //   4: PullIngredientsPayload swapped respectShortageGate (single
        //      bool) for two independent flags: pullAvailable +
        //      triggerAutocraft. New behavior: plain click autocrafts only,
        //      Shift adds pulling.
        //   5: JackFeedbackPayload changed from (item, moved) to
        //      (List<ItemStack> successItems, ItemStack failureItem) so
        //      multiple unique ingredient types each get their own
        //      staggered fan-out success animation.
        final PayloadRegistrar registrar = event.registrar(JackItToMe.MODID).versioned("5");

        registrar.playToServer(
                PullIngredientsPayload.TYPE,
                PullIngredientsPayload.STREAM_CODEC,
                PullIngredientsPayload::handle
        );

        registrar.playToClient(
                JackFeedbackPayload.TYPE,
                JackFeedbackPayload.STREAM_CODEC,
                JackFeedbackPayload::handle
        );

        registrar.playToServer(
                CheckAvailabilityPayload.TYPE,
                CheckAvailabilityPayload.STREAM_CODEC,
                CheckAvailabilityPayload::handle
        );

        registrar.playToClient(
                AvailabilityResponsePayload.TYPE,
                AvailabilityResponsePayload.STREAM_CODEC,
                AvailabilityResponsePayload::handle
        );

        registrar.playToServer(
                RequestAutocraftPayload.TYPE,
                RequestAutocraftPayload.STREAM_CODEC,
                RequestAutocraftPayload::handle
        );

        registrar.playToClient(
                OpenRsAutocraftPayload.TYPE,
                OpenRsAutocraftPayload.STREAM_CODEC,
                OpenRsAutocraftPayload::handle
        );

        registrar.playToClient(
                AutocraftChainPayload.TYPE,
                AutocraftChainPayload.STREAM_CODEC,
                AutocraftChainPayload::handle
        );
    }
}
