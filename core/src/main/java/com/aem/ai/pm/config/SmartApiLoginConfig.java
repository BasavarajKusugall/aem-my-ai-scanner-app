package com.aem.ai.pm.config;


import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "SmartAPI Login Configuration",
        description = "Configuration for Angel One SmartAPI Login Service"
)
public @interface SmartApiLoginConfig {

    @AttributeDefinition(
            name = "Base32 Secret",
            description = "The TOTP Base32 secret extracted from Angel One QR code"
    )
    String base32Secret() default "UQLVH4QY2BDSHAZFYN2NPKC25I";

    @AttributeDefinition(
            name = "Login URL",
            description = "SmartAPI login endpoint"
    )
    String loginUrl() default "https://apiconnect.angelone.in/rest/auth/angelbroking/user/v1/loginByPassword";
}

