package com.aem.system.health;

import com.aem.system.MyEmailService;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.util.Date;

@Component(
        service = Runnable.class,
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = SystemHealthMonitor.Config.class)
public class SystemHealthMonitor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthMonitor.class);

    @ObjectClassDefinition(name = "BSK System Health Monitor Configuration")
    public @interface Config {

        @AttributeDefinition(
                name = "CPU Threshold (%)",
                description = "Trigger alert if CPU exceeds this value",
                defaultValue = "80"
        )
        int cpuThreshold();

        @AttributeDefinition(
                name = "Heap Threshold (%)",
                description = "Trigger alert if Heap memory exceeds this value",
                defaultValue = "85"
        )
        int heapThreshold();

        @AttributeDefinition(
                name = "Alert Email",
                description = "Email to send alerts"
        )
        String alertEmail();

        @AttributeDefinition(name="Cron expression")
        String scheduler_expression() default "0 0/5 * * * ?";

        @AttributeDefinition(name = "Allow concurrent execution")
        boolean scheduler_concurrent() default false;

        @AttributeDefinition(name = "Scheduler name")
        String scheduler_name() default "SystemHealthMonitor";

    }

    private int cpuThreshold;
    private int heapThreshold;
    private String alertEmail;
    private String schedulerExpression;
    private boolean allowConcurrent;

    @Reference
    private MyEmailService myEmailService;

    @Activate
    protected void activate(Config config) {
        cpuThreshold = config.cpuThreshold();
        heapThreshold = config.heapThreshold();
        alertEmail = config.alertEmail();

        log.info("SystemHealthMonitor activated: CPU={}%, Heap={}%, Email={}, Scheduler='{}', Concurrent={}",
                cpuThreshold, heapThreshold, alertEmail, schedulerExpression, allowConcurrent);
    }

    @Override
    public void run() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double cpuLoad = osBean.getProcessCpuLoad() * 100;

            long usedHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long maxHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
            double heapUsage = ((double) usedHeap / maxHeap) * 100;

            StringBuilder alert = new StringBuilder();
            if (cpuLoad > cpuThreshold) {
                alert.append("⚠️ High CPU Usage: ").append(String.format("%.2f", cpuLoad)).append("%\n");
            }
            if (heapUsage > heapThreshold) {
                alert.append("⚠️ High Heap Memory Usage: ").append(String.format("%.2f", heapUsage)).append("%\n");
            }

            if (alert.length() > 0) {
                alert.append("\nTime: ").append(new Date());
                sendEmail("AEM JVM Health Alert", alert.toString());
                log.warn(alert.toString());
            } else {
                log.info("System health OK: CPU={}%, Heap={}%", String.format("%.2f", cpuLoad), String.format("%.2f", heapUsage));
            }

        } catch (Exception e) {
            log.error("Error monitoring system health", e);
        }
    }

    private void sendEmail(String subject, String body) {
        try {
            boolean sent = myEmailService.sendEmail(alertEmail, body, subject, alertEmail);
            if (!sent) {
                log.error("Failed to send alert email to: {}", alertEmail);
            } else {
                log.info("Alert email sent successfully to {}", alertEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send alert email", e);
        }
    }
}
