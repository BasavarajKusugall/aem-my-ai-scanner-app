package com.aem.ai.scanner.servlet;

import com.aem.ai.scanner.dao.DAOFactory;
import com.aem.ai.scanner.model.TelegramConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import static com.aem.ai.scanner.utils.TelegramUtils.extractConfigFromUpdates;

/**
 * Servlet to register or update a Telegram bot config via DAO.
 * Example: /bin/telegram/register?apiKey=<BOT_TOKEN>
 */
@Component(
        service = { Servlet.class },
        property = {
                "sling.servlet.methods=GET",
                "sling.servlet.paths=/bin/telegram/register"
        }
)
public class TelegramRegisterServlet extends SlingAllMethodsServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Reference
    private DAOFactory daoFactory;

    // ANSI colors for logs
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        String apiKey = request.getParameter("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(400);
            writeJson(response, "{\"error\":\"Missing apiKey param\"}");
            logColored(RED, "[ERROR] Missing apiKey param");
            return;
        }
        String purpose = request.getParameter("purpose");


        try {
            // Call Telegram API getMe
            String url = "https://api.telegram.org/bot" + apiKey + "/getUpdates";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (InputStream is = conn.getInputStream()) {
                JsonNode root = MAPPER.readTree(is);
                if (!root.get("ok").asBoolean()) {
                    response.setStatus(502);
                    writeJson(response, "{\"error\":\"Telegram API returned not ok\"}");
                    logColored(YELLOW, "[WARN] Telegram API returned not ok");
                    return;
                }

                JsonNode result = root.get("result");
                if (result.isEmpty()){
                    response.setStatus(502);
                    writeJson(response, "{\"error\":\"Telegram API returned  ok but no results. First send message a to bot and comeback here\"}");
                    logColored(YELLOW, "[WARN] Telegram API returned  ok"+root);
                    return;
                }

                TelegramConfig telegramConfig = extractConfigFromUpdates(root.toString(), apiKey, "OHL");

                long botUserId = telegramConfig.getBotUserId();
                String username = telegramConfig.getUsername();
                telegramConfig.setPurpose(purpose != null ? purpose : "GENERAL");

                // Use DAO to fetch existing config
                List<TelegramConfig> existingConfigs = daoFactory.fetchTelegramBotUserIDConfigs(String.valueOf(botUserId));

                if (existingConfigs != null && !existingConfigs.isEmpty()) {
                    // Update existing
                    TelegramConfig cfg = existingConfigs.get(0);
                    cfg.setBotToken(apiKey);
                    daoFactory.insertOrUpdateTelegramConfig(cfg); // Implement this DAO method
                    logColored(GREEN, "[UPDATE] Updated Telegram config for bot " + username + " (" + botUserId + ")");
                } else {
                    // Insert new
                    daoFactory.insertOrUpdateTelegramConfig(telegramConfig); // Implement this DAO method
                    logColored(GREEN, "[INSERT] Registered new Telegram bot " + username + " (" + botUserId + ")");
                }

                // Send success JSON back


                response.setStatus(200);
                writeJson(response, telegramConfig.toString());
            }

        } catch (Exception e) {
            response.setStatus(500);
            writeJson(response, "{\"error\":\"" + e.getMessage() + "\"}");
            logColored(RED, "[ERROR] Exception while registering Telegram bot: " + e.getMessage());
        }
    }

    private void writeJson(SlingHttpServletResponse response, String json) {
        try {
            response.setContentType("application/json");
            response.getWriter().write(json);
        } catch (Exception ignore) {}
    }

    private void logColored(String color, String message) {
        System.out.println(color + message + RESET);
    }
}
