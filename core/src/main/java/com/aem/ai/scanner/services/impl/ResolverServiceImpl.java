package com.aem.ai.scanner.services.impl;

import com.aem.ai.scanner.services.ResolverService;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

@Component(service = ResolverService.class, immediate = true)
public class ResolverServiceImpl implements ResolverService {

  private static final Logger LOG = LoggerFactory.getLogger(ResolverServiceImpl.class);
  private static final String SYSTEM_READ_WRITE_USER = "read-write-service-user";

  @Reference
  private ResourceResolverFactory resolverFactory;

  @Override
  public ResourceResolver getServiceResolver() {
    return getResourceResolver();
  }


  private ResourceResolver getResourceResolver() {
    String serviceUser = SYSTEM_READ_WRITE_USER;
    Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE,
        serviceUser);
    try {
      return resolverFactory.getServiceResourceResolver(authInfo);
    } catch (LoginException e) {
      LOG.error("Unable to get Service ResourceResolver for user: {}", serviceUser, e);
    }
    return null;
  }

  @Activate
  protected void activate() {
    LOG.info("ServiceResolver activated.");
  }

  @Deactivate
  protected void deactivate() {
    LOG.info("ServiceResolver deactivated.");
  }
}
