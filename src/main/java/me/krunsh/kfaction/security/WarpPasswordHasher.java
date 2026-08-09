package me.krunsh.kfaction.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash PBKDF2 pour mots de passe de warps.
 *
 * Format:
 * pbkdf2-sha256$iterations$saltBase64$hashBase64
 */
public final class WarpPasswordHasher {

    private static final String PREFIX_SHA256 =
            "pbkdf2-sha256";

    private static final String PREFIX_SHA1 =
            "pbkdf2-sha1";

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private WarpPasswordHasher() {
    }

    public static String hash(
            String password,
            int iterations
    ) {
        if (password == null) {
            throw new IllegalArgumentException(
                    "password cannot be null"
            );
        }

        int safeIterations =
                Math.max(
                        10000,
                        Math.min(
                                500000,
                                iterations
                        )
                );

        byte[] salt =
                new byte[16];

        RANDOM.nextBytes(salt);

        Derivation derived =
                derivePreferred(
                        password.toCharArray(),
                        salt,
                        safeIterations,
                        256
                );

        return derived.prefix
                + "$"
                + safeIterations
                + "$"
                + Base64.getEncoder()
                        .withoutPadding()
                        .encodeToString(salt)
                + "$"
                + Base64.getEncoder()
                        .withoutPadding()
                        .encodeToString(
                                derived.bytes
                        );
    }

    public static boolean verify(
            String password,
            String encodedHash
    ) {
        if (password == null
                || encodedHash == null) {
            return false;
        }

        String[] parts =
                encodedHash.split(
                        "\\$"
                );

        if (parts.length != 4
                || (!PREFIX_SHA256.equals(
                        parts[0]
                )
                && !PREFIX_SHA1.equals(
                        parts[0]
                ))) {
            return false;
        }

        int iterations;

        try {
            iterations =
                    Integer.parseInt(
                            parts[1]
                    );
        } catch (NumberFormatException exception) {
            return false;
        }

        if (iterations < 10000
                || iterations > 500000) {
            return false;
        }

        byte[] salt;
        byte[] expected;

        try {
            salt =
                    Base64.getDecoder()
                            .decode(parts[2]);

            expected =
                    Base64.getDecoder()
                            .decode(parts[3]);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        if (salt.length < 8
                || expected.length < 16) {
            return false;
        }

        String algorithm =
                PREFIX_SHA256.equals(
                        parts[0]
                )
                        ? "PBKDF2WithHmacSHA256"
                        : "PBKDF2WithHmacSHA1";

        byte[] actual;

        try {
            actual =
                    derive(
                            password.toCharArray(),
                            salt,
                            iterations,
                            expected.length * 8,
                            algorithm
                    );
        } catch (IllegalStateException exception) {
            return false;
        }

        return MessageDigest.isEqual(
                expected,
                actual
        );
    }

    private static Derivation derivePreferred(
            char[] password,
            byte[] salt,
            int iterations,
            int keyLengthBits
    ) {
        try {
            return new Derivation(
                    PREFIX_SHA256,
                    derive(
                            password,
                            salt,
                            iterations,
                            keyLengthBits,
                            "PBKDF2WithHmacSHA256"
                    )
            );
        } catch (IllegalStateException unavailableSha256) {
            return new Derivation(
                    PREFIX_SHA1,
                    derive(
                            password,
                            salt,
                            iterations,
                            keyLengthBits,
                            "PBKDF2WithHmacSHA1"
                    )
            );
        }
    }

    private static byte[] derive(
            char[] password,
            byte[] salt,
            int iterations,
            int keyLengthBits,
            String algorithm
    ) {
        PBEKeySpec spec =
                new PBEKeySpec(
                        password,
                        salt,
                        iterations,
                        keyLengthBits
                );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(
                            algorithm
                    );

            return factory.generateSecret(
                    spec
            ).getEncoded();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    algorithm + " indisponible",
                    exception
            );
        } finally {
            spec.clearPassword();
        }
    }

    private static final class Derivation {

        private final String prefix;
        private final byte[] bytes;

        private Derivation(
                String prefix,
                byte[] bytes
        ) {
            this.prefix = prefix;
            this.bytes = bytes;
        }
    }
}
