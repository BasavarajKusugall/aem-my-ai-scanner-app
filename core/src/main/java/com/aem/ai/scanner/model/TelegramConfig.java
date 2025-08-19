package com.aem.ai.scanner.model;

public class TelegramConfig {
    private long botChatId;
    private String chatType;
    private String chatTitle;
    private String botName;
    private String botToken;
    private long botUserId;
    private String purpose; // MONITOR, ALERT, etc.
    private boolean isGroupEnabled;

    // --- Getters & Setters ---
    public long getBotChatId() { return botChatId; }
    public void setBotChatId(long botChatId) { this.botChatId = botChatId; }

    public String getChatType() { return chatType; }
    public void setChatType(String chatType) { this.chatType = chatType; }

    public String getChatTitle() { return chatTitle; }
    public void setChatTitle(String chatTitle) { this.chatTitle = chatTitle; }

    public String getBotName() { return botName; }
    public void setBotName(String botName) { this.botName = botName; }

    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }

    public long getBotUserId() { return botUserId; }
    public void setBotUserId(long botUserId) { this.botUserId = botUserId; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public boolean isGroupEnabled() { return isGroupEnabled; }
    public void setGroupEnabled(boolean groupEnabled) { isGroupEnabled = groupEnabled; }
}
