package com.dipak.hearclear;
import org.junit.Test;
import static org.junit.Assert.*;
public class AccessLeaseTest {
    @Test public void transferWindowAndDelayedResponseCannotExtendAccess() {
        AccessLease lease = new AccessLease();
        lease.grant(1000, 11000, 75);
        assertTrue(lease.valid(75999));
        assertFalse(lease.valid(76000));
        lease.grant(1000, 90000, 75);
        assertFalse(lease.valid(90000));
    }
    @Test public void expiredTrialAndBadServerLeaseFailClosed() {
        AccessLease lease = new AccessLease();
        lease.grant(1000, 1000, 0); assertFalse(lease.valid(1000));
        lease.grant(1000, 1000, 76); assertFalse(lease.valid(1000));
        lease.grant(2000, 1000, 60); assertFalse(lease.valid(1000));
    }
    @Test public void sessionRejectionAndRestartHaveNoCachedUnlock() {
        AccessLease lease = new AccessLease();
        lease.grant(1000, 1000, 75); lease.clear();
        assertFalse(lease.valid(1001));
        assertFalse(new AccessLease().valid(1001));
    }
}
