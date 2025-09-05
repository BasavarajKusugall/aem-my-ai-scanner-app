package com.aem.ai.scanner.services.impl;

import com.aem.ai.scanner.model.TradeAnalysis;
import com.aem.ai.scanner.services.GeminiService;
import com.aem.ai.scanner.services.ResolverService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

@Component(
        service = GeminiService.class,
        immediate = true
)
@Designate(ocd = GeminiServiceImpl.Config.class)
public class GeminiServiceImpl implements GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiServiceImpl.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Reference
    private ResolverService serviceResolver;

    private String apiKey;
    private String endpoint;
    private String promptFilePath;
    private String tradeSignalAnalysisPromptFilePath;
    private String portFolioAnalysisPromptFilePath;

    @ObjectClassDefinition(
            name = "BSK Gemini Service Config",
            description = "Configuration for Gemini AI Service"
    )
    public @interface Config {
        @AttributeDefinition(name = "API Key", description = "Gemini API Key")
        String gemini_api_key() default "";

        @AttributeDefinition(name = "Endpoint", description = "Gemini API Endpoint URL")
        String gemini_endpoint() default "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

        @AttributeDefinition(name = "Daily News Prompt File Path", description = "Daily News Path to prompt.txt file in repository or file system")
        String gemini_daily_news_updates_prompt_path() default "/content/ai-scanner/ai/prompts/Morning_Update_Template.txt";

        @AttributeDefinition(name = "Fundament Analysis Prompt File Path", description = "Path to prompt.txt file in repository or file system")
        String gemini_trade_signal_analysis_prompt_path() default "/content/ai-scanner/ai/prompts/gemini_trade_analysis_prompt.txt";

        @AttributeDefinition(name = "Portfolio Analysis Prompt File Path", description = "Path to prompt.txt file in repository or file system")
        String gemini_portfolio_analysis_prompt_path() default "/content/ai-scanner/ai/prompts/portfolio_analysis_template.txt";
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        this.apiKey = PropertiesUtil.toString(config.gemini_api_key(), "");
        this.endpoint = PropertiesUtil.toString(config.gemini_endpoint(), "");
        this.promptFilePath = PropertiesUtil.toString(config.gemini_daily_news_updates_prompt_path(), "");
        this.tradeSignalAnalysisPromptFilePath = PropertiesUtil.toString(config.gemini_trade_signal_analysis_prompt_path(), "");
        this.portFolioAnalysisPromptFilePath = PropertiesUtil.toString(config.gemini_portfolio_analysis_prompt_path(), "");
        log.info("GeminiService activated with endpoint: {}", this.endpoint);
    }
    @Override
    public String analyzePortfolio(String portfolioJson) throws Exception {
        HttpResponse<String> resp = getHttpResponse(portfolioJson, portFolioAnalysisPromptFilePath);

        if (resp.statusCode() == 200) {
            JsonNode geminiResp = mapper.readTree(resp.body());
            return geminiResp.at("/candidates/0/content/parts/0/text").asText("").trim();
        } else {
            log.error("Gemini API error: {} {}", resp.statusCode(), resp.body());
            throw new RuntimeException("Gemini API error: " + resp.statusCode() + " " + resp.body());
        }
    }
    @Override
    public TradeAnalysis tradeSignalAnalysis(String signalMsg) throws Exception {
        log.info("Starting Gemini fundamentalAnalysis for signal...");

        HttpResponse<String> resp = getHttpResponse(signalMsg, tradeSignalAnalysisPromptFilePath);

        if (resp.statusCode() == 200) {
            JsonNode geminiResp = mapper.readTree(resp.body());
            String jsonText = geminiResp.at("/candidates/0/content/parts/0/text").asText("").trim();

            return getTradeAnalysis(jsonText);
        } else {
            log.error("Gemini API error: {} {}", resp.statusCode(), resp.body());
            throw new RuntimeException("Gemini API error: " + resp.statusCode() + " " + resp.body());
        }
    }

    private @NotNull HttpResponse<String> getHttpResponse(String signalMsg,String promptFilePath) throws IOException, InterruptedException {
        String promptTemplate = loadPromptFromRepoOrFS(promptFilePath);

        if (promptTemplate == null) {
            throw new RuntimeException("Failed to load prompt from: " + promptFilePath);
        }
        String prompt = promptTemplate;
        if (StringUtils.isNotEmpty(signalMsg)){
            prompt = promptTemplate+ signalMsg;//String.format(promptTemplate, signalMsg);
        }

        log.debug("Prepared prompt:\n{}", prompt);

        // Build request payload
        String payload = mapper.writeValueAsString(Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                }
        ));

        log.debug("Payload: {}", payload);

        // Create HTTP request
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        log.info("Sending request to Gemini API...");
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Received response: HTTP {}", resp.statusCode());
        return resp;
    }

    private static @NotNull TradeAnalysis getTradeAnalysis(String jsonText) throws JsonProcessingException {
        if (jsonText.startsWith("```")) {
            jsonText = jsonText.replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();
        }

        TradeAnalysis analysis = mapper.readValue(jsonText, TradeAnalysis.class);
        analysis.setResponseJson(jsonText);

        log.info("Successfully parsed TradeAnalysis for symbol: {}", analysis.symbol);
        return analysis;
    }

    @Override
    public String todayNewsUpdates() throws Exception {
        HttpResponse<String> resp = getHttpResponse(StringUtils.EMPTY, tradeSignalAnalysisPromptFilePath);
        if (resp == null) {
            throw new RuntimeException("Failed to get response from Gemini API");
        }
        if (resp.statusCode() == 200) {
            JsonNode geminiResp = mapper.readTree(resp.body());
            String jsonText = geminiResp.at("/candidates/0/content/parts/0/text").asText("").trim();

            return jsonText;
        } else {
            log.error("Gemini API error: {} {}", resp.statusCode(), resp.body());
            throw new RuntimeException("Gemini API error: " + resp.statusCode() + " " + resp.body());
        }
    }

    /**
     * Load prompt from CRX repo if available, else fallback to file system.
     */
    private String loadPromptFromRepoOrFS(String path) {
        try (ResourceResolver resolver = serviceResolver.getServiceResolver()) {
            Resource resource = resolver.getResource(path);

            if (resource != null) {
                log.debug("Loading prompt from repository: {}", path);

                Resource contentRes = resource.getChild("jcr:content");
                if (contentRes != null) {
                    ValueMap vm = contentRes.adaptTo(ValueMap.class);
                    if (vm != null && vm.containsKey("jcr:data")) {
                        try (InputStream is = vm.get("jcr:data", InputStream.class)) {
                            if (is != null) {
                                return IOUtils.toString(is, StandardCharsets.UTF_8);
                            }
                        }
                    }
                }
            } else {
                log.warn("Prompt not found in repository at {}, checking filesystem...", path);
            }

            // fallback → filesystem
            File promptFile = new File(path);
            if (promptFile.exists()) {
                log.debug("Loading prompt from filesystem: {}", path);
                return Files.readString(promptFile.toPath());
            }
        } catch (Exception e) {
            log.error("Error reading prompt from {} ", path, e);
        }
        return null;
    }
}
