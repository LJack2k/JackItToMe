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
        final PayloadRegistrar registrar = event.registrar(JackItToMe.MODID).versioned("1");

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
    }
}
