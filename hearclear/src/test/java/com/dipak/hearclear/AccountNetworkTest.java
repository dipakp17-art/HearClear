package com.dipak.hearclear;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLException;
public class AccountNetworkTest {
    @Test public void tunnelErrorsGiveRetryInstructionsInsteadOfHtml() {
        assertTrue(AccountNetwork.httpError(502).contains("laptop server"));
        assertTrue(AccountNetwork.httpError(404).contains("address"));
        assertTrue(AccountNetwork.httpError(403).contains("blocked"));
        assertTrue(AccountNetwork.httpError(429).contains("one minute"));
    }
    @Test public void connectionErrorsAreActionableWithoutTlsBypass() {
        assertTrue(AccountNetwork.connectionError(new UnknownHostException()).contains("internet"));
        assertTrue(AccountNetwork.connectionError(new SocketTimeoutException()).contains("too long"));
        assertTrue(AccountNetwork.connectionError(new SSLException("test")).contains("HTTPS"));
    }
}
