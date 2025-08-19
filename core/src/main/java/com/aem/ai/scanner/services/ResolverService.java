package com.aem.ai.scanner.services;


import org.apache.sling.api.resource.ResourceResolver;

public interface ResolverService {

  ResourceResolver getServiceResolver();

}