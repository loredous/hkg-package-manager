package hkgpackagemanager.core;

/** A catalog or catalog key that cannot be used; getMessage() is the reason shown to the user. */
public class BadCatalogException extends Exception {
    private static final long serialVersionUID = 1L;

    public BadCatalogException(String reason) {
        super(reason);
    }
}
