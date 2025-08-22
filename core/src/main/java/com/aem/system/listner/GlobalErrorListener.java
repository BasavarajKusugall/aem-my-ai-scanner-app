package com.aem.system.listner;


import com.aem.system.MyEmailService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component(service = LogListener.class, immediate = true)
public class GlobalErrorListener implements LogListener {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorListener.class);
    @Reference
    private MyEmailService myEmailService;

    @Override
    public void logged(LogEntry entry) {
        if (entry.getLevel() == LogService.LOG_ERROR) {
            String message = "JVM Level Error Captured: " + entry.getMessage();
            Throwable exception = entry.getException();

            // Log internally
            log.error(message, exception);

            // Call Email Notification Service
            sendErrorEmail(message, exception);
        }
    }

    private void sendErrorEmail(String message, Throwable ex) {
        try {
            String template = "/content/ai-scanner/email/notifications/system-notification.html";
            Map<String, String> mailContent = new HashMap<>();
            mailContent.put("subject",message);
            mailContent.put("body", ex.getMessage());
          /*  List<String> strings = emailService.sendEmail(template, mailContent, "basavaraj.kusugall@gmail.com");
            if (strings != null && !strings.isEmpty()) {
                log.error("Failed to send alert email to: {}", String.join(", ", strings));
            } else {
                log.info("Alert email sent successfully to {}", message);
            }*/

        } catch (Exception e) {
            log.error("Failed to send alert email", e);
        }
    }
}
