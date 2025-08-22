package com.aem.system;


public interface MyEmailService {
    boolean sendEmail(String to, String subject, String body, String from);
}