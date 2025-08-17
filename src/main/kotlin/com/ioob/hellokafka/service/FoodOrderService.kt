package com.ioob.hellokafka.service

import com.ioob.hellokafka.model.*
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

/**
 * 음식 주문 처리 서비스
 */
@Service
class FoodOrderService(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(FoodOrderService::class.java)

    /**
     * 음식 주문 생성
     */
    fun createFoodOrder(orderRequest: FoodOrderRequest): String {
        val orderId = "FOOD-${UUID.randomUUID().toString().substring(0, 8)}"
        val totalAmount = orderRequest.items.sumOf { it.unitPrice.multiply(BigDecimal(it.quantity)) }

        val orderEvent = FoodOrderEvent(
            orderId = orderId,
            userId = orderRequest.userId,
            restaurantId = orderRequest.restaurantId,
            restaurantName = orderRequest.restaurantName,
            items = orderRequest.items,
            totalAmount = totalAmount,
            deliveryAddress = orderRequest.deliveryAddress,
            customerPhone = orderRequest.customerPhone,
            status = FoodOrderStatus.ORDER_RECEIVED
        )

        logger.info("🍕 음식 주문 생성: orderId=$orderId, restaurant=${orderRequest.restaurantName}")

        // Kafka로 주문 이벤트 발행
        kafkaTemplate.send("food-order-events", orderId, orderEvent)

        // 즉시 주문 접수 알림 발송
        val restaurantName = orderRequest.restaurantName
        notificationService.publishNotification(
            userId = orderRequest.userId,
            type = NotificationType.ORDER_CREATED,
            title = "주문이 접수되었습니다! 🍕",
            message = "${restaurantName}에서 주문을 접수했습니다. 조리를 시작합니다.",
            relatedId = orderId,
            priority = NotificationPriority.HIGH
        )

        return orderId
    }

    /**
     * 주문 상태 업데이트
     */
    fun updateOrderStatus(orderId: String, newStatus: FoodOrderStatus, restaurantName: String = "식당") {
        logger.info("📝 주문 상태 업데이트: orderId=$orderId, status=$newStatus")

        // 상태 업데이트 이벤트 발행
        val statusUpdateEvent = mapOf(
            "orderId" to orderId,
            "status" to newStatus.name,
            "restaurantName" to restaurantName,
            "updatedAt" to System.currentTimeMillis()
        )

        kafkaTemplate.send("food-order-status", orderId, statusUpdateEvent)
    }

    /**
     * 상태별 알림 메시지 생성
     */
    fun getNotificationForStatus(status: FoodOrderStatus, restaurantName: String): Pair<String, String> {
        return when (status) {
            FoodOrderStatus.ORDER_RECEIVED -> 
                "주문 접수 완료! 🛎️" to "${restaurantName}에서 주문을 접수했습니다."
            
            FoodOrderStatus.COOKING_STARTED -> 
                "조리 시작! 👨‍🍳" to "${restaurantName}에서 음식 조리를 시작했습니다."
            
            FoodOrderStatus.COOKING_COMPLETED -> 
                "조리 완료! ✨" to "음식이 완성되었습니다. 배달 기사가 픽업하러 갑니다."
            
            FoodOrderStatus.PICKUP_COMPLETED -> 
                "픽업 완료! 🏃‍♂️" to "배달 기사가 음식을 픽업했습니다. 곧 출발합니다."
            
            FoodOrderStatus.DELIVERY_STARTED -> 
                "배달 시작! 🚗" to "배달 기사가 고객님께 향하고 있습니다."
            
            FoodOrderStatus.DELIVERY_COMPLETED -> 
                "배달 완료! 🎉" to "음식이 안전하게 배달되었습니다. 맛있게 드세요!"
        }
    }

    /**
     * 상태에 따른 알림 타입 반환
     */
    fun getNotificationTypeForStatus(status: FoodOrderStatus): NotificationType {
        return when (status) {
            FoodOrderStatus.ORDER_RECEIVED -> NotificationType.ORDER_CREATED
            FoodOrderStatus.COOKING_STARTED -> NotificationType.ORDER_CONFIRMED
            FoodOrderStatus.COOKING_COMPLETED -> NotificationType.ORDER_CONFIRMED
            FoodOrderStatus.PICKUP_COMPLETED -> NotificationType.SHIPPING_STARTED
            FoodOrderStatus.DELIVERY_STARTED -> NotificationType.SHIPPING_STARTED
            FoodOrderStatus.DELIVERY_COMPLETED -> NotificationType.DELIVERY_COMPLETED
        }
    }
}

/**
 * 음식 주문 상태별 Consumer 서비스
 */
@Service
class FoodOrderConsumerService(
    private val notificationService: NotificationService,
    private val foodOrderService: FoodOrderService
) {
    private val logger = LoggerFactory.getLogger(FoodOrderConsumerService::class.java)

    /**
     * 음식 주문 이벤트 처리
     */
    @org.springframework.kafka.annotation.KafkaListener(
        topics = ["food-order-events"],
        containerFactory = "jsonKafkaListenerContainerFactory"
    )
    fun handleFoodOrderEvent(
        @org.springframework.messaging.handler.annotation.Payload message: Map<String, Any>,
        @org.springframework.messaging.handler.annotation.Header(org.springframework.kafka.support.KafkaHeaders.RECEIVED_KEY, required = false) key: String?
    ) {
        try {
            val orderEvent = mapToFoodOrderEvent(message)
            logger.info("🍕 음식 주문 이벤트 처리: orderId=${orderEvent.orderId}, status=${orderEvent.status}")

            // 첫 주문인 경우 자동으로 다음 단계들을 시뮬레이션
            if (orderEvent.status == FoodOrderStatus.ORDER_RECEIVED) {
                simulateOrderProgress(orderEvent)
            }
        } catch (e: Exception) {
            logger.error("❌ 음식 주문 이벤트 처리 실패", e)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun mapToFoodOrderEvent(map: Map<String, Any>): FoodOrderEvent {
        val itemsList = map["items"] as List<Map<String, Any>>
        val items = itemsList.map { item ->
            FoodItem(
                menuId = item["menuId"] as String,
                menuName = item["menuName"] as String,
                quantity = (item["quantity"] as Number).toInt(),
                unitPrice = java.math.BigDecimal(item["unitPrice"].toString())
            )
        }
        
        return FoodOrderEvent(
            orderId = map["orderId"] as String,
            userId = map["userId"] as String,
            restaurantId = map["restaurantId"] as String,
            restaurantName = map["restaurantName"] as String,
            items = items,
            totalAmount = java.math.BigDecimal(map["totalAmount"].toString()),
            deliveryAddress = map["deliveryAddress"] as String,
            customerPhone = map["customerPhone"] as String,
            status = FoodOrderStatus.valueOf(map["status"] as String)
        )
    }

    /**
     * 주문 상태 업데이트 이벤트 처리
     */
    @org.springframework.kafka.annotation.KafkaListener(
        topics = ["food-order-status"],
        containerFactory = "jsonKafkaListenerContainerFactory"
    )
    fun handleOrderStatusUpdate(
        @org.springframework.messaging.handler.annotation.Payload statusUpdate: Map<String, Any>,
        @org.springframework.messaging.handler.annotation.Header(org.springframework.kafka.support.KafkaHeaders.RECEIVED_KEY, required = false) key: String?
    ) {
        val orderId = statusUpdate["orderId"] as String
        val statusName = statusUpdate["status"] as String
        val restaurantName = statusUpdate["restaurantName"] as? String ?: "식당"
        
        try {
            val status = FoodOrderStatus.valueOf(statusName)
            logger.info("📝 주문 상태 업데이트 처리: orderId=$orderId, status=$status")

            // 해당 주문의 사용자 ID를 찾아야 하는데, 실제로는 DB에서 조회해야 함
            // 여기서는 시뮬레이션을 위해 임시로 설정
            val userId = "USER001" // 실제로는 DB에서 주문 정보로 사용자 조회

            val (title, message) = foodOrderService.getNotificationForStatus(status, restaurantName)
            val notificationType = foodOrderService.getNotificationTypeForStatus(status)

            // 알림 발송
            notificationService.publishNotification(
                userId = userId,
                type = notificationType,
                title = title,
                message = message,
                relatedId = orderId,
                priority = if (status == FoodOrderStatus.DELIVERY_COMPLETED) NotificationPriority.HIGH else NotificationPriority.NORMAL
            )

        } catch (e: IllegalArgumentException) {
            logger.error("❌ 잘못된 주문 상태: $statusName", e)
        }
    }

    /**
     * 주문 진행 과정 시뮬레이션 (실제로는 식당/배달기사 앱에서 상태 업데이트)
     */
    private fun simulateOrderProgress(orderEvent: FoodOrderEvent) {
        logger.info("🎬 주문 진행 시뮬레이션 시작: ${orderEvent.orderId}")

        Thread {
            try {
                // 30초 후 조리 시작
                Thread.sleep(30_000)
                foodOrderService.updateOrderStatus(
                    orderEvent.orderId, 
                    FoodOrderStatus.COOKING_STARTED,
                    orderEvent.restaurantName
                )

                // 2분 후 조리 완료
                Thread.sleep(120_000)
                foodOrderService.updateOrderStatus(
                    orderEvent.orderId, 
                    FoodOrderStatus.COOKING_COMPLETED,
                    orderEvent.restaurantName
                )

                // 30초 후 픽업 완료
                Thread.sleep(30_000)
                foodOrderService.updateOrderStatus(
                    orderEvent.orderId, 
                    FoodOrderStatus.PICKUP_COMPLETED,
                    orderEvent.restaurantName
                )

                // 1분 후 배달 시작
                Thread.sleep(60_000)
                foodOrderService.updateOrderStatus(
                    orderEvent.orderId, 
                    FoodOrderStatus.DELIVERY_STARTED,
                    orderEvent.restaurantName
                )

                // 3분 후 배달 완료
                Thread.sleep(180_000)
                foodOrderService.updateOrderStatus(
                    orderEvent.orderId, 
                    FoodOrderStatus.DELIVERY_COMPLETED,
                    orderEvent.restaurantName
                )

            } catch (e: InterruptedException) {
                logger.warn("주문 시뮬레이션 중단: ${orderEvent.orderId}")
            }
        }.start()
    }
}
