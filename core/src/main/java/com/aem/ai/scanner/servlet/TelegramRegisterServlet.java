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

        try {
            // Call Telegram API getMe
            String url = "https://api.telegram.org/bot" + apiKey + "/getMe";
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
                JsonNode firstMessage = result.get(0).get("message");
                long chatId = 000;
                if (firstMessage != null && firstMessage.has("chat")) {
                     chatId = firstMessage.get("chat").get("id").asLong();
                }
                long botUserId = result.get("id").asLong();
                String botName = result.get("first_name").asText();
                String username = result.has("username") ? result.get("username").asText() : botName;

                // Use DAO to fetch existing config
                List<TelegramConfig> existingConfigs = daoFactory.fetchTelegramBotUserIDConfigs(String.valueOf(botUserId));

                if (existingConfigs != null && !existingConfigs.isEmpty()) {
                    // Update existing
                    TelegramConfig cfg = existingConfigs.get(0);
                    cfg.setBotToken(apiKey);
                    daoFactory.insertOrUpdateTelegramConfig(cfg); // Implement this DAO method
                    logColored(GREEN, "[UPDATE] Updated Telegram config for bot " + botName + " (" + botUserId + ")");
                } else {
                    // Insert new
                    TelegramConfig cfg = new TelegramConfig();
                    cfg.setBotUserId(botUserId);
                    cfg.setBotName(botName);
                    cfg.setBotToken(apiKey);
                    cfg.setBotChatId(chatId);
                    cfg.setChatType("private");
                    cfg.setChatTitle(username);
                    cfg.setPurpose("General Bot");
                    cfg.setGroupEnabled(false);
                    cfg.setPriority(1);
                    cfg.setActive(true);
                    daoFactory.insertOrUpdateTelegramConfig(cfg); // Implement this DAO method
                    logColored(GREEN, "[INSERT] Registered new Telegram bot " + botName + " (" + botUserId + ")");
                }

                // Send success JSON back
                String resultJson = MAPPER.createObjectNode()
                        .put("ok", true)
                        .put("bot_user_id", botUserId)
                        .put("bot_name", botName)
                        .put("username", username)
                        .toString();

                response.setStatus(200);
                writeJson(response, resultJson);
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
