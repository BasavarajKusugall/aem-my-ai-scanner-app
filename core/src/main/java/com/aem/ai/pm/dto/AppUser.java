package com.aem.ai.pm.dto;

public class AppUser {
    private long userId;
    private String externalRef;
    private String email;
    private String fullName;
    private String phone;
    private String status;

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
