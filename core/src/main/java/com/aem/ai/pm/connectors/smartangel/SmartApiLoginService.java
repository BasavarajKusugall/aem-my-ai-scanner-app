package com.aem.ai.pm.connectors.smartangel;

import com.aem.ai.pm.config.SmartApiLoginConfig;
import com.aem.ai.pm.net.HttpClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base32;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component(service = SmartApiLoginService.class, immediate = true)
@Designate(ocd = SmartApiLoginConfig.class)
public class SmartApiLoginService {

    private volatile SmartApiLoginConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    @Reference
    private HttpClientService httpClientService;

    @Activate
    @Modified
    protected void activate(SmartApiLoginConfig config) {
        this.config = config;
    }

    public Map<String, Object> login(String clientId, String password) throws Exception {
        // 1. Generate TOTP
        String totp = generateTOTP(config.base32Secret());

        // 2. Build payload
        Map<String, String> payload = new HashMap<>();
        payload.put("clientcode", clientId);
        payload.put("password", password);
        payload.put("totp", totp);

        String jsonPayload = mapper.writeValueAsString(payload);

        // 3. Build headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        // 4. Call SmartAPI
        String response = httpClientService.post(config.loginUrl(), headers, jsonPayload, 10_000);

        // 5. Parse response
        JsonNode root = mapper.readTree(response);

        if (root.has("data")) {
            JsonNode data = root.get("data");
            Map<String, Object> tokens = new HashMap<>();
            if (data.has("jwtToken")) tokens.put("jwtToken", data.get("jwtToken").asText());
            if (data.has("feedToken")) tokens.put("feedToken", data.get("feedToken").asText());
            if (data.has("refreshToken")) tokens.put("refreshToken", data.get("refreshToken").asText());
            return tokens;
        } else {
            throw new RuntimeException("Login failed: " + response);
        }
    }

    private String generateTOTP(String base32Secret) throws Exception {
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
