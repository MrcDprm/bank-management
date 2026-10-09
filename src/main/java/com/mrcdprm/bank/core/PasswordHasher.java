package com.mrcdprm.bank.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Argon2id şifre özeti (Bouncy Castle). Saklanan biçim PHC standardıdır:
 * $argon2id$v=19$m=19456,t=2,p=1$<tuz>$<özet>
 * Parametreler OWASP önerisi: 19 MiB bellek, 2 tur, 1 iş parçacığı.
 */
public final class PasswordHasher {

    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        final byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        final byte[] hash = derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES);
        return "$argon2id$v=19$m=" + MEMORY_KIB + ",t=" + ITERATIONS + ",p=" + PARALLELISM + "$"
                + ENCODER.encodeToString(salt) + "$" + ENCODER.encodeToString(hash);
    }

    /**
     * Şifre saklanan özetle eşleşiyor mu? Özet bozuksa ya da parametreler makul sınırların dışındaysa
     * (veritabanı dosyası elle değiştirilmiş olabilir) false döner; dev bir bellek isteği uygulamayı kilitlemez.
     */
    public static boolean verify(String password, String encoded) {
        if (password == null || encoded == null)
            return false;
        try {
            final String[] parts = encoded.split("\\$");
            // ["", "argon2id", "v=19", "m=..,t=..,p=..", tuz, özet]
            if (parts.length != 6 || !parts[1].equals("argon2id") || !parts[2].equals("v=19"))
                return false;
            int memory = -1;
            int iterations = -1;
            int parallelism = -1;
            for (String param : parts[3].split(",")) {
                final String[] kv = param.split("=", 2);
                if (kv.length != 2 || !kv[1].matches("\\d{1,7}"))
                    return false;
                final int value = Integer.parseInt(kv[1]);
                switch (kv[0]) {
                    case "m" -> memory = value;
                    case "t" -> iterations = value;
                    case "p" -> parallelism = value;
                    default -> {
                        return false;
                    }
                }
            }
            if (memory < 8 * 1024 || memory > 256 * 1024 || iterations < 1 || iterations > 10
                    || parallelism < 1 || parallelism > 4)
                return false;
            final byte[] salt = DECODER.decode(parts[4]);
            final byte[] expected = DECODER.decode(parts[5]);
            if (salt.length < 8 || salt.length > 64 || expected.length < 16 || expected.length > 64)
                return false;
            final byte[] actual = derive(password, salt, memory, iterations, parallelism, expected.length);
            return MessageDigest.isEqual(actual, expected); // sabit süreli karşılaştırma
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int memory, int iterations, int parallelism, int length) {
        final Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        final Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        final byte[] out = new byte[length];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), out);
        return out;
    }
}
