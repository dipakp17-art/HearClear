package com.dipak.hearclear;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.dipak.hearclear.AccountForm.*;
public class AccountFormTest {
    private final String password = "valid-password-123";
    @Test public void signInDoesNotRequireConsentOrRecovery() {
        assertEquals(AccountForm.Error.NONE, validate(Mode.LOGIN, "deepak", password, "", false));
    }
    @Test public void registrationRequiresExplicitConsent() {
        assertEquals(AccountForm.Error.CONSENT, validate(Mode.REGISTER, "deepak", password, "", false));
        assertEquals(AccountForm.Error.NONE, validate(Mode.REGISTER, "deepak", password, "", true));
    }
    @Test public void transfersAndPasswordResetRequireRecoveryCode() {
        for (Mode mode : new Mode[] { Mode.TRANSFER, Mode.RECOVER }) {
            assertEquals(AccountForm.Error.RECOVERY, validate(mode, "deepak", password, "  ", true));
            assertEquals(AccountForm.Error.NONE, validate(mode, "deepak", password, "private-code", false));
        }
    }
    @Test public void shortPasswordAndInvalidIdNeverReachNetwork() {
        assertEquals(AccountForm.Error.PASSWORD, validate(Mode.LOGIN, "deepak", "short", "", false));
        assertEquals(AccountForm.Error.ACCOUNT, validate(Mode.LOGIN, "a b", password, "", false));
        assertEquals(AccountForm.Error.ACCOUNT, validate(Mode.LOGIN, "abc", password, "", false));
        assertEquals(AccountForm.Error.NONE, validate(Mode.LOGIN, "user_123", password, "", false));
    }
    @Test public void unicodePasswordLengthMatchesPythonServerCodePoints() {
        String sixEmoji = "😀😀😀😀😀😀"; // 12 UTF-16 chars, only 6 password characters
        assertEquals(AccountForm.Error.PASSWORD, validate(Mode.LOGIN, "deepak", sixEmoji, "", false));
        assertEquals(AccountForm.Error.NONE, validate(Mode.LOGIN, "deepak", sixEmoji + "abcdef", "", false));
    }
    @Test public void paymentReferenceMustMatchBackendContract() {
        assertFalse(validReference("123"));
        assertFalse(validReference("reference with spaces"));
        assertTrue(validReference("123456789012"));
        assertTrue(validReference("abCD12345678"));
    }
}
