package com.ecole.saas.communication.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CommunicationGatewayProperties.class)
public class CommunicationGatewayConfig {
}
