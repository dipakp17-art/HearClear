package com.dipak.hearclear;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** Useful retry messages without exposing HTML proxy pages or low-level socket errors. */
public final class AccountNetwork {
    public static String httpError(int code) {
        if (code == 429) return "Too many attempts. Wait one minute and try again.";
        if (code == 404 || code == 410) return "The account server address is no longer available. Ask the owner to check the tunnel address.";
        if (code == 403) return "The account server blocked this connection. Ask the owner to check tunnel access.";
        if (code >= 500) return "The account server is temporarily unavailable. Keep the laptop server and tunnel running, then retry.";
        return "The account server could not complete this request (HTTP " + code + "). Try again.";
    }
    public static String connectionError(Throwable error) {
        if (error instanceof UnknownHostException) return "Cannot find the account server. Check internet and the tunnel address, then retry.";
        if (error instanceof SocketTimeoutException) return "The account server took too long to respond. Check the laptop and internet, then retry.";
        if (error instanceof SSLException) return "A secure connection could not be established. Check the phone date/time and server HTTPS setup.";
        if (error instanceof IOException) return "Could not connect. Check internet and keep the laptop server and tunnel open, then retry.";
        return "Could not complete this action. Please try again.";
    }
}
