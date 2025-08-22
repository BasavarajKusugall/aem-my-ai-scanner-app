package com.aem.system;

import org.osgi.service.component.annotations.Component;


@Component(service = MyEmailService.class, immediate = true)
public class MyEmailServiceImpl implements MyEmailService {


    @Override
    public boolean sendEmail(String to, String subject, String body, String from) {
        try {

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
