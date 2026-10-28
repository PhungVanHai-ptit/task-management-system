package com.intern.taskmanagementsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình chính của ứng dụng:
 *  - JMS + MessageConverter (Jackson JSON)
 *  - JPA Auditing (auto set createdAt, updatedAt)
 *  - Scheduling (@Scheduled), Async (@Async)
 */
@Configuration
@EnableJms
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
public class AppConfig {

    /**
     * MessageConverter: chuyển đổi IssueEventMessage ↔ JSON trong JMS.
     * Thay cho default Java serialization — an toàn và dễ debug hơn.
     */
    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        converter.setObjectMapper(objectMapper);

        // Map type cho từng message class
        Map<String, Class<?>> typeIdMappings = new HashMap<>();
        typeIdMappings.put("IssueEventMessage",
                com.intern.taskmanagementsystem.messaging.IssueEventMessage.class);
        typeIdMappings.put("EmailVerificationMessage",
                com.intern.taskmanagementsystem.messaging.EmailVerificationMessage.class);
        typeIdMappings.put("PasswordResetMessage",
                com.intern.taskmanagementsystem.messaging.PasswordResetMessage.class);
        converter.setTypeIdMappings(typeIdMappings);

        return converter;
    }
}
