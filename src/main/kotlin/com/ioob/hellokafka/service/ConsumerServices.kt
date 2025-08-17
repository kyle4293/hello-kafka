package com.ioob.hellokafka.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ioob.hellokafka.model.*
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service

/**
 * 알림 이벤트를 처리하는 Consumer 서비스
 */
@Service
class NotificationConsumerService(
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(NotificationConsumerService::class.java)

    @KafkaListener(
        topics = ["notification-events"],
        containerFactory = "jsonKafkaListenerContainerFactory"
    )
    fun handleNotificationEvent(
        @Payload message: Map<String, Any>,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?
    ) {
        logger.info("📢 알림 이벤트 처리: userId=${message["userId"]}, type=${message["type"]}")
        
        try {
            // Map을 NotificationEvent로 변환
            val notificationEvent = mapToNotificationEvent(message)
            
            // 실시간 알림 전송 (SSE + DB 저장)
            notificationService.sendRealTimeNotification(notificationEvent)
            
        } catch (e: Exception) {
            logger.error("❌ 알림 처리 실패: ${message["notificationId"]}", e)
        }
    }
    
    private fun mapToNotificationEvent(map: Map<String, Any>): NotificationEvent {
        @Suppress("UNCHECKED_CAST")
        return NotificationEvent(
            notificationId = map["notificationId"] as String,
            userId = map["userId"] as String,
            type = NotificationType.valueOf(map["type"] as String),
            title = map["title"] as String,
            message = map["message"] as String,
            relatedId = map["relatedId"] as? String,
            priority = NotificationPriority.valueOf(map["priority"] as String),
            channels = (map["channels"] as? List<String>)?.map { NotificationChannel.valueOf(it) } ?: listOf(NotificationChannel.EMAIL),
            metadata = map["metadata"] as? Map<String, Any> ?: emptyMap()
        )
    }
}

/**
 * 간단한 메시지를 처리하는 Consumer 서비스
 */
@Service
class MessageConsumerService {
    private val logger = LoggerFactory.getLogger(MessageConsumerService::class.java)

    @KafkaListener(
        topics = ["simple-messages"],
        containerFactory = "stringKafkaListenerContainerFactory"
    )
    fun handleSimpleMessage(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?
    ) {
        logger.info("📨 간단한 메시지 수신:")
        logger.info("  └─ Topic: $topic, Partition: $partition, Offset: $offset")
        logger.info("  └─ Key: $key")
        logger.info("  └─ Message: $message")
        
        // 여기서 실제 비즈니스 로직 처리
        // 예: 로그 저장, 알림 발송, 데이터 처리 등
        
        // 처리 시간 시뮬레이션
        Thread.sleep(100)
        
        logger.info("✅ 메시지 처리 완료: $message")
    }
}

/**
 * 주문 이벤트를 처리하는 Consumer 서비스
 */
@Service
class OrderConsumerService(
    private val inventoryProducerService: InventoryProducerService,
    private val paymentProducerService: PaymentProducerService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(OrderConsumerService::class.java)

    @KafkaListener(
        topics = ["order-events"],
        containerFactory = "jsonKafkaListenerContainerFactory"
    )
    fun handleOrderEvent(
        @Payload message: Any,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long
    ) {
        try {
            logger.info("🛒 주문 이벤트 수신: partition=$partition, offset=$offset, key=$key")
            
            when (message) {
                is OrderEvent -> {
                    handleNewOrder(message)
                }
                is Map<*, *> -> {
                    // 주문 상태 업데이트 등의 다른 이벤트 처리
                    handleOrderUpdate(message)
                }
                else -> {
                    logger.warn("⚠️ 알 수 없는 주문 이벤트 타입: ${message::class.java}")
                }
            }
            
        } catch (e: Exception) {
            logger.error("❌ 주문 이벤트 처리 중 오류 발생: ${e.message}", e)
        }
    }

    private fun handleNewOrder(orderEvent: OrderEvent) {
        logger.info("🆕 새로운 주문 처리 시작:")
        logger.info("  └─ 주문 ID: ${orderEvent.orderId}")
        logger.info("  └─ 상품: ${orderEvent.productName} (${orderEvent.productId})")
        logger.info("  └─ 수량: ${orderEvent.quantity}")
        logger.info("  └─ 총 금액: ${orderEvent.totalAmount}")

        // 1. 재고 확인 및 차감
        logger.info("📦 재고 처리 중...")
        val currentStock = 100 // 실제로는 DB에서 조회
        val newStock = currentStock - orderEvent.quantity
        
        if (newStock >= 0) {
            // 재고 차감 이벤트 발행
            inventoryProducerService.updateInventory(
                productId = orderEvent.productId,
                productName = orderEvent.productName,
                quantityChanged = -orderEvent.quantity,
                remainingStock = newStock
            )
            
            // 2. 결제 처리 요청
            logger.info("💳 결제 처리 요청 중...")
            paymentProducerService.processPayment(
                orderId = orderEvent.orderId,
                userId = orderEvent.userId,
                amount = orderEvent.totalAmount,
                paymentMethod = "CREDIT_CARD"
            )
            
        } else {
            logger.warn("⚠️ 재고 부족: 요청 수량=${orderEvent.quantity}, 현재 재고=$currentStock")
            // 실제로는 주문 실패 이벤트를 발행해야 함
        }

        logger.info("✅ 주문 처리 완료: ${orderEvent.orderId}")
    }

    private fun handleOrderUpdate(updateEvent: Map<*, *>) {
        val orderId = updateEvent["orderId"] as? String
        val status = updateEvent["status"] as? String
        
        logger.info("📝 주문 상태 업데이트: orderId=$orderId, status=$status")
        
        // 실제로는 DB 업데이트 등의 로직이 들어감
    }
}

/**
 * 재고 업데이트 이벤트를 처리하는 Consumer 서비스
 */
@Service
class InventoryConsumerService {
    private val logger = LoggerFactory.getLogger(InventoryConsumerService::class.java)

    @KafkaListener(
        topics = ["inventory-updates"],
        containerFactory = "jsonKafkaListenerContainerFactory"
    )
    fun handleInventoryUpdate(
        @Payload inventoryEvent: InventoryUpdateEvent,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int
    ) {
        logger.info("📦 재고 업데이트 이벤트 처리:")
        logger.info("  └─ 상품 ID: ${inventoryEvent.productId}")
        logger.info("  └─ 상품명: ${inventoryEvent.productName}")
        logger.info("  └─ 변경 수량: ${inventoryEvent.quantityChanged}")
        logger.info("  └─ 남은 재고: ${inventoryEvent.remainingStock}")
        logger.info("  └─ 파티션: $partition")

        // 실제 재고 DB 업데이트 로직
        updateInventoryInDatabase(inventoryEvent)
        
        // 재고 부족 알림 체크
        if (inventoryEvent.remainingStock < 10) {
            logger.warn("⚠️ 재고 부족 알림: ${inventoryEvent.productName} (남은 재고: ${inventoryEvent.remainingStock})")
            // 실제로는 알림 서비스나 관리자에게 알림을 보내는 로직이 들어감
            sendLowStockAlert(inventoryEvent)
        }

        logger.info("✅ 재고 업데이트 처리 완료: ${inventoryEvent.productId}")
    }

    private fun updateInventoryInDatabase(inventoryEvent: InventoryUpdateEvent) {
        // 실제 DB 업데이트 로직 시뮬레이션
        Thread.sleep(50)
        logger.info("💾 DB 재고 업데이트 완료: ${inventoryEvent.productId}")
    }

    private fun sendLowStockAlert(inventoryEvent: InventoryUpdateEvent) {
        logger.info("🚨 재고 부족 알림 발송: ${inventoryEvent.productName}")
        // 실제로는 이메일, 슬랙, SMS 등으로 알림 발송
    }
}

/**
 * 결제 이벤트를 처리하는 Consumer 서비스
 */
@Service
class PaymentConsumerService(
    private val orderProducerService: OrderProducerService
) {
    private val logger = LoggerFactory.getLogger(PaymentConsumerService::class.java)

    @KafkaListener(
        topics = ["payment-events"],
        containerFactory = "jsonKafkaListenerContainerFactory"
    )
    fun handlePaymentEvent(
        @Payload paymentEvent: PaymentEvent,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int
    ) {
        logger.info("💳 결제 이벤트 처리:")
        logger.info("  └─ 주문 ID: ${paymentEvent.orderId}")
        logger.info("  └─ 사용자 ID: ${paymentEvent.userId}")
        logger.info("  └─ 결제 금액: ${paymentEvent.amount}")
        logger.info("  └─ 결제 방법: ${paymentEvent.paymentMethod}")
        logger.info("  └─ 결제 상태: ${paymentEvent.status}")
        logger.info("  └─ 파티션: $partition")

        when (paymentEvent.status) {
            PaymentStatus.SUCCESS -> {
                handleSuccessfulPayment(paymentEvent)
            }
            PaymentStatus.FAILED -> {
                handleFailedPayment(paymentEvent)
            }
            PaymentStatus.PENDING -> {
                handlePendingPayment(paymentEvent)
            }
        }

        logger.info("✅ 결제 이벤트 처리 완료: ${paymentEvent.orderId}")
    }

    private fun handleSuccessfulPayment(paymentEvent: PaymentEvent) {
        logger.info("🎉 결제 성공 처리: ${paymentEvent.orderId}")
        
        // 주문 상태를 '결제 완료'로 업데이트
        orderProducerService.updateOrderStatus(paymentEvent.orderId, OrderStatus.PAID)
        
        // 결제 완료 관련 후속 작업
        // - 영수증 발송
        // - 배송 준비
        // - 포인트 적립 등
        
        processOrderShipping(paymentEvent.orderId)
    }

    private fun handleFailedPayment(paymentEvent: PaymentEvent) {
        logger.warn("❌ 결제 실패 처리: ${paymentEvent.orderId}")
        
        // 주문 상태를 '결제 실패'로 업데이트
        orderProducerService.updateOrderStatus(paymentEvent.orderId, OrderStatus.CANCELLED)
        
        // 결제 실패 관련 후속 작업
        // - 재고 복구
        // - 사용자에게 실패 알림
        // - 다른 결제 방법 제안 등
        
        restoreInventoryForFailedPayment(paymentEvent.orderId)
    }

    private fun handlePendingPayment(paymentEvent: PaymentEvent) {
        logger.info("⏳ 결제 대기 중: ${paymentEvent.orderId}")
        
        // 주문 상태를 '결제 대기'로 업데이트
        orderProducerService.updateOrderStatus(paymentEvent.orderId, OrderStatus.PAYMENT_PENDING)
    }

    private fun processOrderShipping(orderId: String) {
        logger.info("🚚 배송 처리 시작: $orderId")
        // 실제로는 배송 시스템과 연동
        
        // 배송 시작 상태로 업데이트
        Thread.sleep(1000) // 배송 처리 시뮬레이션
        orderProducerService.updateOrderStatus(orderId, OrderStatus.SHIPPED)
    }

    private fun restoreInventoryForFailedPayment(orderId: String) {
        logger.info("🔄 결제 실패로 인한 재고 복구: $orderId")
        // 실제로는 주문 정보를 조회해서 재고를 복구해야 함
        // 여기서는 로그만 출력
    }
}

/**
 * 모든 이벤트를 모니터링하는 Consumer 서비스
 * (실제 운영에서는 모니터링 시스템이나 대시보드용)
 */
@Service
class EventMonitoringService {
    private val logger = LoggerFactory.getLogger(EventMonitoringService::class.java)
    
    // 여러 토픽을 동시에 구독
    @KafkaListener(
        topics = ["order-events", "inventory-updates", "payment-events"],
        groupId = "monitoring-group",
        containerFactory = "jsonKafkaListenerContainerFactory"
    )
    fun monitorAllEvents(
        @Payload message: Any,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) timestamp: Long
    ) {
        // 모니터링용 로그 (간단하게 출력)
        logger.debug("📊 [MONITOR] Topic: $topic, Partition: $partition, Offset: $offset, Timestamp: $timestamp")
        
        // 실제로는 여기서 메트릭 수집, 대시보드 업데이트 등을 수행
        // - 처리량 측정
        // - 에러율 계산
        // - 지연시간 모니터링
        // - 알림 발송 등
    }
}
