package com.aem.ai.scanner.utils;

import com.aem.ai.scanner.model.TelegramConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TelegramUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Extracts a fully populated TelegramConfig from getUpdates JSON.
     *
     * @param getUpdatesJson JSON string from Telegram API /getUpdates
     * @param botToken       Bot token used for authentication
     * @return TelegramConfig object with botUserId, botChatId, botName, botToken set
     * @throws Exception if parsing fails or no valid bot/chat ID found
     */
    public static TelegramConfig extractConfigFromUpdates(String getUpdatesJson, String botToken, String purpose) throws Exception {
        TelegramConfig cfg = new TelegramConfig();
        cfg.setBotToken(botToken);

        JsonNode root = MAPPER.readTree(getUpdatesJson);
        Long botUserId = null;
        Long botChatId = null;
        String botName = null;
        String username = null;

        if (root.has("ok") && root.get("ok").asBoolean() && root.has("result")) {
            JsonNode results = root.get("result");
            if (results.isArray()) {
                for (JsonNode update : results) {
                    // 1️⃣ Check "my_chat_member" for bot info
                    if (update.has("my_chat_member")) {
                        JsonNode newChatMember = update.get("my_chat_member").get("new_chat_member").get("user");
                        JsonNode chat = update.get("my_chat_member").get("chat");

                        if (newChatMember != null && newChatMember.get("is_bot").asBoolean()) {
                            botUserId = newChatMember.get("id").asLong();
                            botName = newChatMember.get("first_name").asText();
                            username = newChatMember.has("username") ? newChatMember.get("username").asText() : botName;
                        }
                        if (chat != null && chat.has("id")) {
                            botChatId = chat.get("id").asLong();
                        }
                    }

                    // 2️⃣ Check "message" node for bot info
                    if (update.has("message")) {
                        JsonNode message = update.get("message");
                        JsonNode chat = message.get("chat");
                        JsonNode newChatMember = message.has("new_chat_member") ? message.get("new_chat_member").get("user") : null;

                        if (newChatMember != null && newChatMember.get("is_bot").asBoolean()) {
                            botUserId = newChatMember.get("id").asLong();
                            botName = newChatMember.get("first_name").asText();
                            username = newChatMember.has("username") ? newChatMember.get("username").asText() : botName;
                        }

                        if (chat != null && chat.has("id")) {
                            botChatId = chat.get("id").asLong();
                        }
                    }

                    // Stop if both IDs found
                    if (botUserId != null && botChatId != null) break;
                }
            }
        }

        if (botUserId == null || botChatId == null) {
            throw new RuntimeException("Cannot extract botUserId or botChatId from getUpdates JSON");
        }

        cfg.setBotUserId(botUserId);
        cfg.setBotChatId(botChatId);
        cfg.setBotName(botName);
        cfg.setUsername(username);
        cfg.setChatType("group"); // default, adjust if needed
        cfg.setChatTitle("Telegram Bot Chat"); // default
        cfg.setPurpose(purpose);
        cfg.setGroupEnabled(true);

        return cfg;
    }
}
