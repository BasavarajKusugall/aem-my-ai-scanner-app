package com.aem.ai.scanner.services.impl;


import com.aem.ai.scanner.dao.TradeDAO;
import com.aem.ai.scanner.model.TelegramConfig;
import com.aem.ai.scanner.services.HttpService;
import com.aem.ai.scanner.services.TelegramService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component(immediate = true,service = TelegramService.class )
public class TelegramServiceImpl implements TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramServiceImpl.class);

    @Reference
    private HttpService http;

    @Reference
    private TradeDAO tradeDAO; // Injected via OSGi

    private static final int TELEGRAM_MESSAGE_LIMIT = 4096;


    /** Send message to ALL telegram accounts */
    public void sendMessage(String text) {
        List<TelegramConfig> configs = tradeDAO.fetchTelegramConfigs();
        for (TelegramConfig cfg : configs) {
            sendToConfig(cfg, text);
        }
    }
    /** Send message to ALL telegram accounts */
    public void sendMessageDailyNews(String text) {
        List<TelegramConfig> configs = tradeDAO.fetchTelegramDailyNewsConfigs();
        for (TelegramConfig cfg : configs) {
            sendToConfig(cfg, text);
        }
    }
    /** Send message to ALL telegram accounts */
    public void sendMessageDailyStocksAlerts(String text) {
        List<TelegramConfig> configs = tradeDAO.fetchTelegramDailyAlertsConfigs();
        for (TelegramConfig cfg : configs) {
            sendToConfig(cfg, text);
        }
    }
    /** Send message to ALL telegram accounts */
    public void sendMessageDailyCryptoAlerts(String text) {
        List<TelegramConfig> configs = tradeDAO.fetchTelegramDailyCryptoAlertsConfigs();
        for (TelegramConfig cfg : configs) {
            sendToConfig(cfg, text);
        }
    }

    /** Send only to MONITOR purpose accounts */
    public void sendMonitorLog(String level, String text) {
        List<TelegramConfig> configs = tradeDAO.fetchTelegramMonitorConfigs();
        for (TelegramConfig cfg : configs) {
            if ("MONITOR".equalsIgnoreCase(cfg.getPurpose())) {
                String decorated = "[" + level + "] " + text;
                sendToConfig(cfg, decorated);
            }
        }
    }

    @Override
    public void sendMessageToUser(String telegramBotUserId, String message) {
        List<TelegramConfig> configs = tradeDAO.fetchTelegramBotUserIDConfigs(telegramBotUserId);
        for (TelegramConfig cfg : configs) {
            sendToConfig(cfg, message);
        }
    }

    private void sendToConfig(TelegramConfig cfg, String text) {
        try {
            // Step 1: Split into chunks first
            List<String> chunks = new ArrayList<>();
            int length = text.length();
            int start = 0;

            while (start < length) {
                int end = Math.min(start + TELEGRAM_MESSAGE_LIMIT, length);

                // Break at last space if possible
                if (end < length) {
                    int lastSpace = text.lastIndexOf(' ', end);
                    if (lastSpace > start) {
                        end = lastSpace;
                    }
                }

                String chunk = text.substring(start, end).trim();
                chunks.add(chunk);
                start = end;
            }

            // Step 2: Send each chunk with footer (Part X/Y)
            int totalChunks = chunks.size();
            for (int i = 0; i < totalChunks; i++) {
                String chunk = chunks.get(i);
                String footer = "\n\n(Part " + (i + 1) + "/" + totalChunks + ")";

                // Ensure we don't exceed Telegram’s 4096 char limit
                if (chunk.length() + footer.length() > TELEGRAM_MESSAGE_LIMIT) {
                    chunk = chunk.substring(0, TELEGRAM_MESSAGE_LIMIT - footer.length()).trim();
                }

                String finalChunk = chunk + footer;

                String apiUrl = "https://api.telegram.org/bot" + cfg.getBotToken() + "/sendMessage";
                String url = apiUrl + "?chat_id=" + URLEncoder.encode(String.valueOf(cfg.getBotChatId()), StandardCharsets.UTF_8)
                        + "&text=" + URLEncoder.encode(finalChunk, StandardCharsets.UTF_8);

                log.info("Sending to {} ({}) [Chunk {}/{}]",
                        cfg.getChatTitle(), cfg.getPurpose(), (i + 1), totalChunks);

                String res = http.get(url);
                log.info("Sent chunk {}/{} to {} ({}) response={}",
                        (i + 1), totalChunks, cfg.getChatTitle(), cfg.getPurpose(), res);

                // Add delay if more chunks are left
                if (i < totalChunks - 1) {
                    try {
                        Thread.sleep(200); // 200 ms delay
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Chunk sending interrupted", ie);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to send to {} ({})", cfg.getChatTitle(), cfg.getPurpose(), e);
        }
    }

}
