package hkgpackagemanager.core;

/** A compatibility colour with the reason shown to the user. */
public final class Verdict {
    public final Colour colour;
    public final String reason;

    public Verdict(Colour colour, String reason) {
        this.colour = colour;
        this.reason = reason;
    }
}
