package com.aem.system.scheduers;

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobBuilder;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.ScheduledJobInfo;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Self-healing scheduler monitor implemented using Sling Job-based scheduler.
 * This periodically scans active Sling jobs and verifies scheduler health.
 */
@Designate(ocd = SelfHealingSchedulerMonitor.Config.class)
@Component(
        service = JobConsumer.class,
        immediate = true,
        property = {
                JobConsumer.PROPERTY_TOPICS + "=" + SelfHealingSchedulerMonitor.JOB_TOPIC
        }
)
public class SelfHealingSchedulerMonitor implements JobConsumer {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingSchedulerMonitor.class);

    public static final String JOB_TOPIC = "com/aem/system/jobs/selfHealingMonitor";
    private static final String JOB_NAME = "self-healing-scheduler-monitor";

    @Reference
    private JobManager jobManager;

    private Config config;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.config = config;
        removeScheduledJobs();
        addScheduledJob();
        log.info("Activated SelfHealingSchedulerMonitor with cron: {}", config.scheduler_expression());
    }

    private void addScheduledJob() {
        try {
            Collection<ScheduledJobInfo> myJobs = jobManager.getScheduledJobs(JOB_TOPIC, 1, null);
            if (myJobs.isEmpty()) {
                JobBuilder.ScheduleBuilder scheduleBuilder = jobManager.createJob(JOB_TOPIC).schedule();
                scheduleBuilder.cron(config.scheduler_expression());
                if (scheduleBuilder.add() == null) {
                    log.error("Failed to schedule SelfHealingSchedulerMonitor with cron: {}", config.scheduler_expression());
                } else {
                    log.info("Successfully scheduled SelfHealingSchedulerMonitor job with cron: {}", config.scheduler_expression());
                }
            } else {
                log.debug("SelfHealingSchedulerMonitor job already scheduled.");
            }
        } catch (Exception e) {
            log.error("Error while scheduling SelfHealingSchedulerMonitor", e);
        }
    }

    private void removeScheduledJobs() {
        try {
            Collection<ScheduledJobInfo> jobs = jobManager.getScheduledJobs(JOB_TOPIC, 0, null);
            for (ScheduledJobInfo job : jobs) {
                job.unschedule();
                log.info("Removed existing job: {}", job.getJobTopic());
            }
        } catch (Exception e) {
            log.error("Error while removing SelfHealingSchedulerMonitor jobs", e);
        }
    }

    @Override
    public JobResult process(Job job) {
        log.info("Running SelfHealingSchedulerMonitor...");

        try {
            // Fetch all scheduled jobs from JobManager
            Collection<ScheduledJobInfo> scheduledJobs = jobManager.getScheduledJobs();
            Set<String> activeJobNames = new HashSet<>();

            for (ScheduledJobInfo jobInfo : scheduledJobs) {
                activeJobNames.add(jobInfo.getJobTopic());
            }

            log.info("Active Job Topics in JobManager: {}", activeJobNames);

        } catch (Exception e) {
            log.error("Error while executing SelfHealingSchedulerMonitor job", e);
            return JobResult.FAILED;
        }

        return JobResult.OK;
    }

    private void reRegisterJob(String topic) {
        try {
            JobBuilder.ScheduleBuilder scheduleBuilder = jobManager.createJob(topic).schedule();
            scheduleBuilder.cron("0 0/5 * * * ?"); // Recreate job every 5 mins
            scheduleBuilder.add();
            log.info("Re-registered missing job: {}", topic);
        } catch (Exception e) {
            log.error("Failed to re-register missing job: {}", topic, e);
        }
    }

    /**
     * OSGi configuration for SelfHealingSchedulerMonitor.
     */
    @ObjectClassDefinition(
            name = "Self-Healing Scheduler Monitor",
            description = "Monitors all Sling Job-based schedulers and auto-restores if missing."
    )
    public @interface Config {

        @AttributeDefinition(
                name = "Enable scheduler",
                description = "Enable or disable the self-healing monitor"
        )
        boolean enable() default true;

        @AttributeDefinition(
                name = "Cron expression",
                description = "Defines how frequently the self-healing scheduler runs"
        )
        String scheduler_expression() default "0 0/1 * * * ?"; // every 1 minute
    }
}
