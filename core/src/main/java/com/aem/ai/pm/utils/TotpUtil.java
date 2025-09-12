package com.aem.ai.pm.utils;

import org.apache.commons.codec.binary.Base32;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;

public class TotpUtil {

    public static String generateTOTP(String base32Secret) throws Exception {
        Base32 base32 = new Base32();
        byte[] secretKey = base32.decode(base32Secret);

        long timeWindow = Instant.now().getEpochSecond() / 30;
        byte[] data = ByteBuffer.allocate(8).putLong(timeWindow).array();

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secretKey, "HmacSHA1"));
        byte[] hmac = mac.doFinal(data);

        int offset = hmac[hmac.length - 1] & 0x0F;
        int binary = ((hmac[offset] & 0x7f) << 24) |
                ((hmac[offset + 1] & 0xff) << 16) |
                ((hmac[offset + 2] & 0xff) << 8) |
                (hmac[offset + 3] & 0xff);

        int otp = binary % 1_000_000;
        return String.format("%06d", otp);
    }
}
