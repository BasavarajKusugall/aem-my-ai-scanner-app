package com.aem.ai.scanner.services;


/**
 * TelegramService interface defines methods for sending messages
 * to different Telegram configurations.
 */
public interface TelegramService {

    /** Send message to ALL telegram accounts */
    void sendMessage(String text);

    /** Send message to ALL telegram daily news accounts */
    void sendMessageDailyNews(String text);

    /** Send message to ALL telegram daily stock alerts accounts */
    void sendMessageDailyStocksAlerts(String text);

    /** Send message to ALL telegram daily crypto alerts accounts */
    void sendMessageDailyCryptoAlerts(String text);

    /** Send message to monitor purpose accounts */
    void sendMonitorLog(String level, String text);
}
