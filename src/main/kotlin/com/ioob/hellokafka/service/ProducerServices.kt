package com.ioob.hellokafka.service

import com.ioob.hellokafka.model.*
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * 간단한 메시지를 보내는 Producer 서비스
 */
@Service
class MessageProducerService(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(MessageProducerService::class.java)

    /**
     * 간단한 문자열 메시지 전송
     */
    fun sendSimpleMessage(message: String): CompletableFuture<SendResult<String, Any>> {
        logger.info("📤 간단한 메시지 전송: $message")
        
        return kafkaTemplate.send("simple-messages", message).whenComplete { result, exception ->
            if (exception == null) {
                logger.info("✅ 메시지 전송 성공: offset=${result.recordMetadata.offset()}")
            } else {
                logger.error("❌ 메시지 전송 실패: ${exception.message}")
            }
        }
    }

    /**
     * 키-값 형태의 메시지 전송
     */
    fun sendMessageWithKey(key: String, message: String): CompletableFuture<SendResult<String, Any>> {
        logger.info("📤 키-값 메시지 전송: key=$key, message=$message")
        
        return kafkaTemplate.send("simple-messages", key, message).whenComplete { result, exception ->
            if (exception == null) {
                logger.info("✅ 키-값 메시지 전송 성공: partition=${result.recordMetadata.partition()}, offset=${result.recordMetadata.offset()}")
            } else {
                logger.error("❌ 키-값 메시지 전송 실패: ${exception.message}")
            }
        }
    }
}

/**
 * 주문 관련 이벤트를 처리하는 Producer 서비스
 */
@Service
class OrderProducerService(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(OrderProducerService::class.java)

    /**
     * 새로운 주문 생성 및 이벤트 발행
     */
    fun createOrder(orderRequest: OrderRequest): String {
        val orderId = "ORDER-${UUID.randomUUID().toString().substring(0, 8)}"
        val totalAmount = orderRequest.unitPrice.multiply(BigDecimal(orderRequest.quantity))

        val orderEvent = OrderEvent(
            orderId = orderId,
            userId = orderRequest.userId,
            productId = orderRequest.productId,
            productName = orderRequest.productName,
            quantity = orderRequest.quantity,
            unitPrice = orderRequest.unitPrice,
            totalAmount = totalAmount,
            status = OrderStatus.CREATED
        )

        logger.info("🛒 새로운 주문 생성: $orderId")
        
        // 주문 ID를 키로 사용하여 같은 주문의 이벤트들이 같은 파티션에 가도록 함
        kafkaTemplate.send("order-events", orderId, orderEvent).whenComplete { result, exception ->
            if (exception == null) {
                logger.info("✅ 주문 이벤트 발행 성공: orderId=$orderId, partition=${result.recordMetadata.partition()}")
            } else {
                logger.error("❌ 주문 이벤트 발행 실패: orderId=$orderId, error=${exception.message}")
            }
        }

        return orderId
    }

    /**
     * 주문 상태 업데이트 이벤트 발행
     */
    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        logger.info("📝 주문 상태 업데이트: orderId=$orderId, status=$newStatus")
        
        // 실제 구현에서는 DB에서 주문 정보를 조회해야 하지만, 
        // 예제에서는 간단한 상태 업데이트 이벤트만 발행
        val updateEvent = mapOf(
            "orderId" to orderId,
            "status" to newStatus.name,
            "updatedAt" to System.currentTimeMillis()
        )

        kafkaTemplate.send("order-events", orderId, updateEvent)
    }
}

/**
 * 재고 관련 이벤트를 처리하는 Producer 서비스
 */
@Service
class InventoryProducerService(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(InventoryProducerService::class.java)

    /**
     * 재고 업데이트 이벤트 발행
     */
    fun updateInventory(productId: String, productName: String, quantityChanged: Int, remainingStock: Int) {
        val inventoryEvent = InventoryUpdateEvent(
            productId = productId,
            productName = productName,
            quantityChanged = quantityChanged,
            remainingStock = remainingStock
        )

        logger.info("📦 재고 업데이트 이벤트 발행: productId=$productId, quantityChanged=$quantityChanged, remaining=$remainingStock")

        kafkaTemplate.send("inventory-updates", productId, inventoryEvent).whenComplete { result, exception ->
            if (exception == null) {
                logger.info("✅ 재고 업데이트 이벤트 발행 성공: productId=$productId")
            } else {
                logger.error("❌ 재고 업데이트 이벤트 발행 실패: productId=$productId, error=${exception.message}")
            }
        }
    }
}

/**
 * 결제 관련 이벤트를 처리하는 Producer 서비스
 */
@Service
class PaymentProducerService(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(PaymentProducerService::class.java)

    /**
     * 결제 처리 이벤트 발행
     */
    fun processPayment(orderId: String, userId: String, amount: BigDecimal, paymentMethod: String): String {
        val paymentId = "PAY-${UUID.randomUUID().toString().substring(0, 8)}"
        
        // 결제 성공/실패를 랜덤으로 시뮬레이션 (실제로는 결제 게이트웨이 연동)
        val isSuccess = Random().nextBoolean()
        val paymentStatus = if (isSuccess) PaymentStatus.SUCCESS else PaymentStatus.FAILED

        val paymentEvent = PaymentEvent(
            orderId = orderId,
            userId = userId,
            amount = amount,
            paymentMethod = paymentMethod,
            status = paymentStatus
        )

        logger.info("💳 결제 처리 이벤트 발행: orderId=$orderId, status=$paymentStatus, amount=$amount")

        kafkaTemplate.send("payment-events", orderId, paymentEvent).whenComplete { result, exception ->
            if (exception == null) {
                logger.info("✅ 결제 이벤트 발행 성공: orderId=$orderId, paymentStatus=$paymentStatus")
            } else {
                logger.error("❌ 결제 이벤트 발행 실패: orderId=$orderId, error=${exception.message}")
            }
        }

        return paymentId
    }
}
