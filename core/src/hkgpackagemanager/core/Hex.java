package hkgpackagemanager.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Lowercase hex and SHA-256 helpers. */
public final class Hex {
    private Hex() {}

    public static String of(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }

    public static String sha256(byte[] data) {
        try {
            return of(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("no SHA-256");
        }
    }
}
