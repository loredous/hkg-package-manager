package hkgpackagemanager.core;

/** A package that cannot be used; getMessage() is the reason shown to the user. */
public class BadPackageException extends Exception {
    private static final long serialVersionUID = 1L;

    public BadPackageException(String reason) {
        super(reason);
    }
}
