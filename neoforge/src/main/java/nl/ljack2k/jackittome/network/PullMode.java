package nl.ljack2k.jackittome.network;

/**
 * How much of each ingredient to pull on one click.
 * <p>
 * Decided client-side from modifier keys and shipped to the server in the packet.
 */
public enum PullMode {
    /** One craft's worth — exactly the count the recipe asks for. */
    SINGLE,
    /** Up to a full stack of each ingredient. */
    STACK,
    /** Everything available, capped only by inventory space. */
    MAX;

    public static PullMode fromOrdinal(int o) {
        PullMode[] v = values();
        return v[Math.floorMod(o, v.length)];
    }
}
