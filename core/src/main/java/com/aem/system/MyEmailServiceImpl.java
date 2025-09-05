package com.aem.system;

import com.aem.ai.scanner.services.TelegramService;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


@Component(service = MyEmailService.class, immediate = true)
public class MyEmailServiceImpl implements MyEmailService {
    @Reference
    private TelegramService telegramService;

    @Override
    public boolean sendEmail(String to, String subject, String body, String from) {
        try {
            if (StringUtils.isNotEmpty(body)){
                telegramService.sendMonitorLog("ERROR",body);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
