package com.aem.system.health;


import com.aem.system.MyEmailService;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

import java.util.*;

@Component(service = Runnable.class, immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {"scheduler.expression=*/300 * * * * ?"})
@Designate(ocd = SystemHealthMonitor.Config.class)
public class SystemHealthMonitor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthMonitor.class);

    @ObjectClassDefinition(name = "System Health Monitor")
    public @interface Config {
        @AttributeDefinition(name = "CPU Threshold (%)", defaultValue = "80")
        int cpuThreshold();

        @AttributeDefinition(name = "Heap Threshold (%)", defaultValue = "85")
        int heapThreshold();

        @AttributeDefinition(name = "Alert Email")
        String alertEmail();
    }

    private int cpuThreshold;
    private int heapThreshold;
    private String alertEmail;

    @Reference
    private MyEmailService myEmailService;

    @Activate
    protected void activate(Config config) {
        cpuThreshold = config.cpuThreshold();
        heapThreshold = config.heapThreshold();
        alertEmail = config.alertEmail();
    }

    @Override
    public void run() {
        try {
            sendEmail("AEM JVM Health Alert", "Test email from AEM JVM Health Monitor");
            OperatingSystemMXBean osBean =
                    (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double cpuLoad = osBean.getProcessCpuLoad() * 100;

            long usedHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long maxHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
            double heapUsage = ((double) usedHeap / maxHeap) * 100;

            StringBuilder alert = new StringBuilder();
            if (cpuLoad > cpuThreshold) {
                alert.append("⚠️ High CPU Usage: ").append(cpuLoad).append("%\n");
            }
            if (heapUsage > heapThreshold) {
                alert.append("⚠️ High Heap Memory Usage: ").append(heapUsage).append("%\n");
            }

            if (alert.length() > 0) {
                alert.append("\nTime: ").append(new Date());
                sendEmail("AEM JVM Health Alert", alert.toString());
                log.warn(alert.toString());
            }

        } catch (Exception e) {
            log.error("Error monitoring system health", e);
        }
    }

    private void sendEmail(String subject, String body) {
        try {
            String template = "/content/ai-scanner/email/notifications/system-notification.html";
            Map<String, String> mailContent = new HashMap<>();
            mailContent.put("subject", subject);
            mailContent.put("body", body);
            boolean strings = myEmailService.sendEmail("basu.sk7@gmail.com", "Hello test mail", "Subject","basavaraj.kusugall@gmail.com");
            if (!strings) {
                log.error("Failed to send alert email to: {}", alertEmail);
            } else {
                log.info("Alert email sent successfully to {}", alertEmail);
            }

        } catch (Exception e) {
            log.error("Failed to send alert email", e);
        }
    }
}
