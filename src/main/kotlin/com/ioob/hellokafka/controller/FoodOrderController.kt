package com.ioob.hellokafka.controller

import com.ioob.hellokafka.entity.NotificationEntity
import com.ioob.hellokafka.model.*
import com.ioob.hellokafka.service.FoodOrderService
import com.ioob.hellokafka.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.math.BigDecimal

/**
 * 음식 주문 및 알림 API 컨트롤러
 */
@RestController
@RequestMapping("/api/food")
@CrossOrigin(origins = ["*"])
class FoodOrderController(
    private val foodOrderService: FoodOrderService,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(FoodOrderController::class.java)

    /**
     * 음식 주문 생성
     * POST /api/food/order
     */
    @PostMapping("/order")
    fun createFoodOrder(@RequestBody orderRequest: FoodOrderRequest): ResponseEntity<Map<String, Any>> {
        logger.info("🍕 음식 주문 API 호출: restaurant=${orderRequest.restaurantName}, user=${orderRequest.userId}")
        
        return try {
            val orderId = foodOrderService.createFoodOrder(orderRequest)
            val totalAmount = orderRequest.items.sumOf { it.unitPrice.multiply(BigDecimal(it.quantity)) }
            
            ResponseEntity.ok(mapOf(
                "status" to "SUCCESS",
                "message" to "주문이 성공적으로 접수되었습니다.",
                "orderId" to orderId,
                "orderDetails" to mapOf(
                    "restaurantName" to orderRequest.restaurantName,
                    "items" to orderRequest.items,
                    "totalAmount" to totalAmount,
                    "deliveryAddress" to orderRequest.deliveryAddress
                )
            ))
        } catch (e: Exception) {
            logger.error("음식 주문 생성 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(mapOf(
                "status" to "ERROR",
                "message" to "주문 처리 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * 주문 상태 업데이트 (식당/배달기사용)
     * PUT /api/food/order/{orderId}/status
     */
    @PutMapping("/order/{orderId}/status")
    fun updateOrderStatus(
        @PathVariable orderId: String,
        @RequestParam status: String,
        @RequestParam(required = false) restaurantName: String?
    ): ResponseEntity<Map<String, Any>> {
        logger.info("📝 주문 상태 업데이트 API: orderId=$orderId, status=$status")
        
        return try {
            val orderStatus = FoodOrderStatus.valueOf(status.uppercase())
            foodOrderService.updateOrderStatus(orderId, orderStatus, restaurantName ?: "식당")
            
            ResponseEntity.ok(mapOf(
                "status" to "SUCCESS",
                "message" to "주문 상태가 업데이트되었습니다.",
                "orderId" to orderId,
                "newStatus" to orderStatus.name
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf(
                "status" to "ERROR",
                "message" to "잘못된 주문 상태입니다. 사용 가능한 상태: ${FoodOrderStatus.values().joinToString(", ")}"
            ))
        } catch (e: Exception) {
            logger.error("주문 상태 업데이트 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(mapOf(
                "status" to "ERROR",
                "message" to "상태 업데이트 중 오류가 발생했습니다: ${e.message}"
            ))
        }
    }

    /**
     * 샘플 음식 주문 생성 (테스트용)
     * POST /api/food/sample-order
     */
    @PostMapping("/sample-order")
    fun createSampleOrder(@RequestParam(required = false) userId: String?): ResponseEntity<Map<String, Any>> {
        val sampleUserId = userId ?: "USER001"
        
        val sampleOrder = FoodOrderRequest(
            userId = sampleUserId,
            restaurantId = "REST001",
            restaurantName = "맛있는 치킨집",
            items = listOf(
                FoodItem("MENU001", "양념치킨", 1, BigDecimal("18000")),
                FoodItem("MENU002", "콜라", 2, BigDecimal("2000"))
            ),
            deliveryAddress = "서울시 강남구 테헤란로 123",
            customerPhone = "010-1234-5678"
        )
        
        return createFoodOrder(sampleOrder)
    }
}

/**
 * 실시간 알림 API 컨트롤러
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = ["*"])
class NotificationController(
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(NotificationController::class.java)

    /**
     * SSE 연결 생성 (실시간 알림 받기)
     * GET /api/notifications/stream?userId=USER001
     */
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamNotifications(@RequestParam userId: String): SseEmitter {
        logger.info("📡 SSE 연결 요청: userId=$userId")
        return notificationService.createEmitterForUser(userId)
    }

    /**
     * 사용자 알림 목록 조회
     * GET /api/notifications?userId=USER001
     */
    @GetMapping("")
    fun getUserNotifications(@RequestParam userId: String): ResponseEntity<Map<String, Any>> {
        logger.info("📋 알림 목록 조회: userId=$userId")
        
        val notifications = notificationService.getUserNotifications(userId)
        val unreadCount = notificationService.getUnreadCount(userId)
        
        return ResponseEntity.ok(mapOf(
            "status" to "SUCCESS",
            "userId" to userId,
            "unreadCount" to unreadCount,
            "notifications" to notifications.map { notification ->
                mapOf(
                    "id" to notification.notificationId,
                    "type" to notification.type.name,
                    "title" to notification.title,
                    "message" to notification.message,
                    "relatedId" to notification.relatedId,
                    "priority" to notification.priority.name,
                    "isRead" to notification.isRead,
                    "createdAt" to notification.createdAt,
                    "readAt" to notification.readAt
                )
            }
        ))
    }

    /**
     * 알림 읽음 처리
     * PUT /api/notifications/{notificationId}/read
     */
    @PutMapping("/{notificationId}/read")
    fun markAsRead(@PathVariable notificationId: String): ResponseEntity<Map<String, Any>> {
        logger.info("📖 알림 읽음 처리: notificationId=$notificationId")
        
        return try {
            notificationService.markAsRead(notificationId)
            ResponseEntity.ok(mapOf(
                "status" to "SUCCESS",
                "message" to "알림이 읽음 처리되었습니다."
            ))
        } catch (e: Exception) {
            logger.error("알림 읽음 처리 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(mapOf(
                "status" to "ERROR",
                "message" to "읽음 처리 중 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 테스트 알림 발송
     * POST /api/notifications/test
     */
    @PostMapping("/test")
    fun sendTestNotification(@RequestParam userId: String): ResponseEntity<Map<String, Any>> {
        logger.info("🧪 테스트 알림 발송: userId=$userId")
        
        notificationService.publishNotification(
            userId = userId,
            type = NotificationType.SYSTEM_ALERT,
            title = "테스트 알림 📢",
            message = "실시간 알림 시스템이 정상적으로 작동합니다!",
            priority = NotificationPriority.NORMAL
        )
        
        return ResponseEntity.ok(mapOf(
            "status" to "SUCCESS",
            "message" to "테스트 알림이 발송되었습니다."
        ))
    }

    /**
     * 연결 상태 확인
     * GET /api/notifications/status
     */
    @GetMapping("/status")
    fun getConnectionStatus(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "connectedUsers" to notificationService.getConnectedUserCount(),
            "status" to "ACTIVE",
            "timestamp" to System.currentTimeMillis()
        ))
    }
}
