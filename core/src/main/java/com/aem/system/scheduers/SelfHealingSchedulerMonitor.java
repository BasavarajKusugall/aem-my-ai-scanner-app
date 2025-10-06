package com.aem.system.scheduers;

import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.ScheduledJobInfo;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component(
        service = Runnable.class,
        immediate = true,
        property = {
                "scheduler.name=SelfHealingSchedulerMonitor",
                "scheduler.expression=0 0/1 * * * ?" // every 1 minute
        }
)
public class SelfHealingSchedulerMonitor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingSchedulerMonitor.class);

    @Reference
    private Scheduler scheduler;

    @Reference
    private JobManager jobManager;

    @Override
    public void run() {
        try {
            log.info("Running SelfHealingSchedulerMonitor...");

            // Get all scheduled jobs from JobManager
            Collection<ScheduledJobInfo> scheduledJobs = jobManager.getScheduledJobs();
            Set<String> activeJobNames = new HashSet<>();
            for (ScheduledJobInfo jobInfo : scheduledJobs) {
                activeJobNames.add(jobInfo.getJobTopic());
            }
            log.info("Active jobs in JobManager: {}", activeJobNames);


        } catch (Exception e) {
            log.error("Error in SelfHealingSchedulerMonitor", e);
        }
    }
}
