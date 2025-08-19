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
import java.util.List;

@Component(immediate = true,service = TelegramService.class )
public class TelegramServiceImpl implements TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramServiceImpl.class);

    @Reference
    private HttpService http;

    @Reference
    private TradeDAO tradeDAO; // Injected via OSGi


    /** Send message to ALL telegram accounts */
    public void sendMessage(String text) {
        List<TelegramConfig> configs = tradeDAO.fetchTelegramConfigs();
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

    private void sendToConfig(TelegramConfig cfg, String text) {
        try {
            String apiUrl = "https://api.telegram.org/bot" + cfg.getBotToken() + "/sendMessage";
            String url = apiUrl + "?chat_id=" + URLEncoder.encode(String.valueOf(cfg.getBotChatId()), StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            String res = http.get(url);
            log.info("Sent to {} ({}) response={}", cfg.getChatTitle(), cfg.getPurpose(), res);
        } catch (Exception e) {
            log.error("Failed to send to {} ({})", cfg.getChatTitle(), cfg.getPurpose(), e);
        }
    }
}
