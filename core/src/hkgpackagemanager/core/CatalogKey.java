package hkgpackagemanager.core;

import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;

/** An RSA public key that signs a catalog index; the fingerprint is the SHA-256 of its SPKI. */
public final class CatalogKey {
    private static final String BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String END = "-----END PUBLIC KEY-----";
    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    public final byte[] spki;
    public final String fingerprint;
    private final PublicKey key;

    private CatalogKey(byte[] spki, PublicKey key) {
        this.spki = spki;
        this.key = key;
        this.fingerprint = Hex.sha256(spki);
    }

    public static CatalogKey fromPem(String pem) throws BadCatalogException {
        int b = pem == null ? -1 : pem.indexOf(BEGIN);
        int e = b < 0 ? -1 : pem.indexOf(END, b);
        if (e < 0) throw new BadCatalogException("not a public key");
        byte[] der = base64(pem.substring(b + BEGIN.length(), e));
        PublicKey key;
        try {
            key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new BadCatalogException("not an RSA key");
        }
        if (!(key instanceof RSAPublicKey)) throw new BadCatalogException("not an RSA key");
        if (((RSAPublicKey) key).getModulus().bitLength() < 2048) {
            throw new BadCatalogException("key too short");
        }
        return new CatalogKey(der, key);
    }

    /** True only when signature is a valid SHA-256 RSA signature of data; false on any error. */
    public boolean verifies(byte[] data, byte[] signature) {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(key);
            s.update(data);
            return s.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] base64(String text) throws BadCatalogException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int acc = 0;
        int bits = 0;
        int pad = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') continue;
            if (c == '=') {
                pad++;
                continue;
            }
            int v = B64.indexOf(c);
            if (v < 0 || pad > 0) throw new BadCatalogException("not a public key");
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((acc >> bits) & 0xff);
            }
        }
        if (pad > 2) throw new BadCatalogException("not a public key");
        return out.toByteArray();
    }
}
