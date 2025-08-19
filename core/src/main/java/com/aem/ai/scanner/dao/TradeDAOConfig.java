package com.aem.ai.scanner.dao;


import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "TradeDAO Configuration")
public @interface TradeDAOConfig {
    @AttributeDefinition(name = "DataSource Name")
    String datasourceName() default "Upstox_TradeBook";
}
