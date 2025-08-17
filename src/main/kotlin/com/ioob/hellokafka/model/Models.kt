package com.ioob.hellokafka.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 음식 주문 요청 모델
 */
data class FoodOrderRequest(
    val userId: String,
    val restaurantId: String,
    val restaurantName: String,
    val items: List<FoodItem>,
    val deliveryAddress: String,
    val customerPhone: String
)

/**
 * 음식 아이템
 */
data class FoodItem(
    val menuId: String,
    val menuName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

/**
 * 음식 주문 이벤트
 */
data class FoodOrderEvent(
    val orderId: String,
    val userId: String,
    val restaurantId: String,
    val restaurantName: String,
    val items: List<FoodItem>,
    val totalAmount: BigDecimal,
    val deliveryAddress: String,
    val customerPhone: String,
    val status: FoodOrderStatus,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 음식 주문 상태
 */
enum class FoodOrderStatus {
    ORDER_RECEIVED,    // 주문 접수
    COOKING_STARTED,   // 조리 시작
    COOKING_COMPLETED, // 조리 완료
    PICKUP_COMPLETED,  // 픽업 완료 (배달기사)
    DELIVERY_STARTED,  // 배달 시작
    DELIVERY_COMPLETED // 배달 완료
}

/**
 * 주문 생성 요청 모델
 */
data class OrderRequest(
    val userId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

/**
 * 주문 이벤트 모델 - Kafka에서 주고받는 메시지
 */
data class OrderEvent(
    val orderId: String,
    val userId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val totalAmount: BigDecimal,
    val status: OrderStatus = OrderStatus.CREATED,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 주문 상태
 */
enum class OrderStatus {
    CREATED,        // 주문 생성됨
    PAYMENT_PENDING, // 결제 대기
    PAID,           // 결제 완료
    SHIPPED,        // 배송 시작
    DELIVERED,      // 배송 완료
    CANCELLED       // 취소됨
}

/**
 * 재고 업데이트 이벤트
 */
data class InventoryUpdateEvent(
    val productId: String,
    val productName: String,
    val quantityChanged: Int,  // 음수면 차감, 양수면 증가
    val remainingStock: Int,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 결제 이벤트
 */
data class PaymentEvent(
    val orderId: String,
    val userId: String,
    val amount: BigDecimal,
    val paymentMethod: String,
    val status: PaymentStatus,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val processedAt: LocalDateTime = LocalDateTime.now()
)

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}

/**
 * 간단한 메시지 요청 모델
 */
data class MessageRequest(
    val message: String
)

/**
 * 간단한 메시지 응답 모델
 */
data class MessageResponse(
    val status: String,
    val message: String
)

/**
 * 알림 이벤트 모델
 */
data class NotificationEvent(
    val notificationId: String,
    val userId: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val relatedId: String? = null,  // 주문ID, 결제ID 등
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val channels: List<NotificationChannel> = listOf(NotificationChannel.EMAIL),
    val metadata: Map<String, Any> = emptyMap(),
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 알림 타입
 */
enum class NotificationType {
    ORDER_CREATED,      // 주문 생성
    ORDER_CONFIRMED,    // 주문 확정
    PAYMENT_SUCCESS,    // 결제 성공
    PAYMENT_FAILED,     // 결제 실패
    SHIPPING_STARTED,   // 배송 시작
    DELIVERY_COMPLETED, // 배송 완료
    INVENTORY_LOW,      // 재고 부족
    SYSTEM_ALERT        // 시스템 알림
}

/**
 * 알림 우선순위
 */
enum class NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * 알림 채널
 */
enum class NotificationChannel {
    EMAIL,
    SMS,
    PUSH,
    WEBHOOK,
    LOG
}
