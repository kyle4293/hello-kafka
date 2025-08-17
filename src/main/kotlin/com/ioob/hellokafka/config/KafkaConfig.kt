package com.ioob.hellokafka.config

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
@EnableKafka
class KafkaConfig {

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

    /**
     * Kafka 토픽들을 자동으로 생성
     */
    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers
        )
        return KafkaAdmin(configs)
    }

    /**
     * 간단한 메시지용 토픽
     */
    @Bean
    fun simpleMessageTopic(): NewTopic {
        return NewTopic("simple-messages", 3, 1)
    }

    /**
     * 주문 이벤트용 토픽
     */
    @Bean
    fun orderEventsTopic(): NewTopic {
        return NewTopic("order-events", 3, 1)
    }

    /**
     * 재고 업데이트용 토픽
     */
    @Bean
    fun inventoryUpdatesTopic(): NewTopic {
        return NewTopic("inventory-updates", 3, 1)
    }

    /**
     * 알림 이벤트용 토픽
     */
    @Bean
    fun notificationEventsTopic(): NewTopic {
        return NewTopic("notification-events", 3, 1)
    }

    /**
     * 음식 주문 이벤트용 토픽
     */
    @Bean
    fun foodOrderEventsTopic(): NewTopic {
        return NewTopic("food-order-events", 3, 1)
    }

    /**
     * 음식 주문 상태 업데이트용 토픽
     */
    @Bean
    fun foodOrderStatusTopic(): NewTopic {
        return NewTopic("food-order-status", 3, 1)
    }

    /**
     * Producer Factory 설정
     */
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            // 성능 최적화 설정
            ProducerConfig.ACKS_CONFIG to "1",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.BATCH_SIZE_CONFIG to 16384,
            ProducerConfig.LINGER_MS_CONFIG to 1,
            ProducerConfig.BUFFER_MEMORY_CONFIG to 33554432
        )
        return DefaultKafkaProducerFactory(configProps)
    }

    /**
     * Kafka Template
     */
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }

    /**
     * Consumer Factory 설정 - 문자열 메시지용
     */
    @Bean
    fun stringConsumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "hello-kafka-string-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
        )
        return DefaultKafkaConsumerFactory(props)
    }

    /**
     * Consumer Factory 설정 - JSON 객체용
     */
    @Bean
    fun jsonConsumerFactory(): ConsumerFactory<String, Any> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "hello-kafka-json-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            // JSON 역직렬화 설정 - 모든 클래스 허용
            JsonDeserializer.TRUSTED_PACKAGES to "*",
            JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
            JsonDeserializer.VALUE_DEFAULT_TYPE to "java.lang.Object"
        )
        return DefaultKafkaConsumerFactory(props)
    }

    /**
     * Kafka Listener Container Factory - 문자열 메시지용
     */
    @Bean("stringKafkaListenerContainerFactory")
    fun stringKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = stringConsumerFactory()
        return factory
    }

    /**
     * Kafka Listener Container Factory - JSON 객체용
     */
    @Bean("jsonKafkaListenerContainerFactory")
    fun jsonKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = jsonConsumerFactory()
        return factory
    }
}
