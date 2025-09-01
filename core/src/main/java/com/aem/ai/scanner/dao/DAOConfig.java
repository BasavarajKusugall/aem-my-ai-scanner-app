package com.aem.ai.scanner.dao;


import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "BSK DAO Configuration")
public @interface DAOConfig {
    @AttributeDefinition(name = "DataSource Name")
    String datasourceName() default "algo_db";
}
