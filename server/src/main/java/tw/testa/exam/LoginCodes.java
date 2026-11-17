package tw.testa.exam;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Six-digit candidate login codes. Only a bcrypt hash is stored: a code is shown once, when it is issued,
 * and slips are printed then. Six digits cannot resist offline guessing, so the hash keeps codes out of
 * the staff pages and backups; online guessing is stopped by the sign-in lockout.
 * Cost 8 keeps issuing a whole roster and a burst of sign-ins fast.
 */
public final class LoginCodes {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(8);

    private LoginCodes() {}

    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static String hash(String code) {
        return ENCODER.encode(code);
    }

    /** Hashes several codes on all cores (a roster of hundreds would otherwise take seconds). */
    public static List<String> hashAll(List<String> codes) {
        return codes.parallelStream().map(LoginCodes::hash).toList();
    }

    public static boolean matches(String code, String hash) {
        return hash != null && code.matches("\\d{6}") && ENCODER.matches(code, hash);
    }
}
