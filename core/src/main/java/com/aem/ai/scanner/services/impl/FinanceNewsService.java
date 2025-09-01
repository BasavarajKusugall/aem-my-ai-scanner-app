package com.aem.ai.scanner.services.impl;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

@ObjectClassDefinition(
        name = "BSK Finance News Service Configuration",
        description = "Configuration for Finance News Service fetching from Perplexity API"
)
@interface FinanceNewsServiceConfig {

    @AttributeDefinition(name = "API URL", description = "Endpoint URL for the finance news API")
    String api_url() default "https://api.perplexity.ai/chat/completions";

    @AttributeDefinition(name = "Model", description = "Model to use for finance news generation")
    String model() default "sonar-pro";

    @AttributeDefinition(name = "Prompt File Path",
            description = "Absolute or relative path to prompt.txt file (e.g., /apps/aem-stk/resources/prompt.txt or /opt/aem/prompts/prompt.txt)")
    String prompt_file_path() default "/apps/aemStkScannerApp/components/config/Morning_Update_Template.txt";

    @AttributeDefinition(name = "Max Tokens", description = "Maximum tokens in response")
    int max_tokens() default 500;

    @AttributeDefinition(name = "Enable Service", description = "Enable/disable API call")
    boolean enabled() default true;
}

@Component(service = FinanceNewsService.class, immediate = true)
@Designate(ocd = FinanceNewsServiceConfig.class)
public class FinanceNewsService {

    private static final Logger log = LoggerFactory.getLogger(FinanceNewsService.class);

    private String apiUrl;
    private String model;
    private String promptFilePath;
    private int maxTokens;
    private boolean enabled;

    @Activate
    @Modified
    protected void activate(FinanceNewsServiceConfig config) {
        this.apiUrl = config.api_url();
        this.model = config.model();
        this.promptFilePath = config.prompt_file_path();
        this.maxTokens = config.max_tokens();
        this.enabled = config.enabled();

        log.info("✅ FinanceNewsService activated with API={}, Model={}, Tokens={}, PromptFile={}, Enabled={}",
                apiUrl, model, maxTokens, promptFilePath, enabled);
    }

    private String readPromptFromFile() {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(promptFilePath));
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.error("❌ Failed to read prompt file at {}", promptFilePath, e);
        }
        return "Give me the latest market finance news updates in 5 bullet points."; // fallback
    }

    public String fetchFinanceNews(String apiKey) {
        if (!enabled) {
            log.warn("⚠️ FinanceNewsService is disabled via OSGi config.");
            return null;
        }

        String prompt = readPromptFromFile();

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String payload = String.format("{"
                            + "\"model\": \"%s\","
                            + "\"messages\": ["
                            + "    {\"role\": \"system\", \"content\": \"You are a financial news assistant.\"},"
                            + "    {\"role\": \"user\", \"content\": \"%s\"}"
                            + "],"
                            + "\"max_tokens\": %d"
                            + "}",
                    model, prompt.replace("\"", "\\\""), maxTokens);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            } else {
                log.error("❌ FinanceNewsService API call failed: HTTP {}", responseCode);
            }
        } catch (Exception e) {
            log.error("❌ Error calling Perplexity API: ", e);
        }
        return null;
    }
}
