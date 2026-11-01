package com.dipak.hearclear;

/** Validate before sending credentials; these rules match the server's identity contract. */
public final class AccountForm {
    public enum Mode { LOGIN, REGISTER, TRANSFER, RECOVER }
    public enum Error { NONE, ACCOUNT, PASSWORD, RECOVERY, CONSENT }
    public static Error validate(Mode mode, String id, String password, String recovery, boolean consent) {
        if (!id.matches("[a-z0-9][a-z0-9_.-]{3,39}")) return Error.ACCOUNT;
        int chars = password.codePointCount(0, password.length());
        if (chars < 12 || chars > 128) return Error.PASSWORD;
        if ((mode == Mode.TRANSFER || mode == Mode.RECOVER) && recovery.trim().isEmpty()) return Error.RECOVERY;
        if (mode == Mode.REGISTER && !consent) return Error.CONSENT;
        return Error.NONE;
    }
    public static boolean validReference(String reference) { return reference.matches("[A-Za-z0-9]{8,40}"); }
}
