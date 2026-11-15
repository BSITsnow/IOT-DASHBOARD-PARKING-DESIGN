package parking.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void correctPasswordVerifies() {
        String stored = PasswordHasher.hash("s3cret-pass");
        assertTrue(PasswordHasher.verify("s3cret-pass", stored));
    }

    @Test
    void wrongPasswordIsRejected() {
        String stored = PasswordHasher.hash("s3cret-pass");
        assertFalse(PasswordHasher.verify("s3cret-pasS", stored));
        assertFalse(PasswordHasher.verify("", stored));
    }

    @Test
    void sameDifferentSaltAndNoPlaintext() {
        String a = PasswordHasher.hash("same-password");
        String b = PasswordHasher.hash("same-password");
        assertNotEquals(a, b);                                   // random salt each time
        assertFalse(a.contains("same-password"));
    }

    @Test
    void garbageStoredValueIsRejected() {
        assertFalse(PasswordHasher.verify("x", "not-a-hash"));
        assertFalse(PasswordHasher.verify("x", null));
    }
}
