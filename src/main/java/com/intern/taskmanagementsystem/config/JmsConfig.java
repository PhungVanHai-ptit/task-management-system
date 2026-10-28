package com.intern.taskmanagementsystem.config;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

/**
 * Cấu hình JMS ActiveMQ Artemis:
 * - AddressSettings: Redelivery policy (max 3 delivery attempts: 1 initial + 2 retries với backoff 10s, 30s, 60s).
 * - ListenerContainerFactory: Bật sessionTransacted(true) để khi Listener ném exception,
 *   message sẽ được rollback và Broker tự động redeliver theo đúng lịch trình mà KHÔNG block thread worker.
 */
@Configuration
public class JmsConfig {

    @Bean
    public ArtemisConfigurationCustomizer artemisConfigurationCustomizer() {
        return configuration -> {
            AddressSettings addressSettings = new AddressSettings();
            // 1 lần nhận ban đầu + 2 lần retry = tối đa 3 lần phân phối
            addressSettings.setMaxDeliveryAttempts(3);
            addressSettings.setRedeliveryDelay(10000L);        // Retry 1: 10 giây
            addressSettings.setRedeliveryMultiplier(3.0);      // Hệ số nhân 3: 10s -> 30s -> 90s
            addressSettings.setMaxRedeliveryDelay(60000L);     // Giới hạn trần: tối đa 60 giây (1 phút)
            addressSettings.setDeadLetterAddress(SimpleString.of("DLQ")); // Hết 3 lần chuyển vào Dead Letter Queue

            // Áp dụng cho mọi address / queue
            configuration.addAddressSetting("#", addressSettings);
        };
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer
    ) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // Bắt buộc bật transaction session để khi ném exception sẽ rollback và kích hoạt broker redelivery
        factory.setSessionTransacted(true);
        return factory;
    }
}
