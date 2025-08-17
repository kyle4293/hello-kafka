package com.ioob.hellokafka.controller

import com.ioob.hellokafka.model.*
import com.ioob.hellokafka.service.MessageProducerService
import com.ioob.hellokafka.service.OrderProducerService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

/**
 * Kafka 메시지를 테스트할 수 있는 REST API 컨트롤러
 */
@RestController
@RequestMapping("/api/kafka")
@CrossOrigin(origins = ["*"])
class KafkaController(
    private val messageProducerService: MessageProducerService,
    private val orderProducerService: OrderProducerService
) {
    private val logger = LoggerFactory.getLogger(KafkaController::class.java)

    /**
     * 간단한 메시지 전송 API
     * POST /api/kafka/message
     */
    @PostMapping("/message")
    fun sendMessage(@RequestBody request: MessageRequest): ResponseEntity<MessageResponse> {
        logger.info("🌐 API 요청: 메시지 전송 - ${request.message}")
        
        return try {
            messageProducerService.sendSimpleMessage(request.message)
            ResponseEntity.ok(
                MessageResponse(
                    status = "SUCCESS",
                    message = "메시지가 성공적으로 전송되었습니다."
                )
            )
        } catch (e: Exception) {
            logger.error("메시지 전송 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                MessageResponse(
                    status = "ERROR",
                    message = "메시지 전송 중 오류가 발생했습니다: ${e.message}"
                )
            )
        }
    }

    /**
     * 키-값 메시지 전송 API
     * POST /api/kafka/message/keyed
     */
    @PostMapping("/message/keyed")
    fun sendKeyedMessage(
        @RequestParam key: String,
        @RequestBody request: MessageRequest
    ): ResponseEntity<MessageResponse> {
        logger.info("🌐 API 요청: 키-값 메시지 전송 - key: $key, message: ${request.message}")
        
        return try {
            messageProducerService.sendMessageWithKey(key, request.message)
            ResponseEntity.ok(
                MessageResponse(
                    status = "SUCCESS",
                    message = "키-값 메시지가 성공적으로 전송되었습니다."
                )
            )
        } catch (e: Exception) {
            logger.error("키-값 메시지 전송 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                MessageResponse(
                    status = "ERROR",
                    message = "키-값 메시지 전송 중 오류가 발생했습니다: ${e.message}"
                )
            )
        }
    }

    /**
     * 새로운 주문 생성 API
     * POST /api/kafka/order
     */
    @PostMapping("/order")
    fun createOrder(@RequestBody orderRequest: OrderRequest): ResponseEntity<Map<String, Any>> {
        logger.info("🌐 API 요청: 주문 생성 - ${orderRequest.productName}")
        
        return try {
            val orderId = orderProducerService.createOrder(orderRequest)
            
            val response = mapOf(
                "status" to "SUCCESS",
                "message" to "주문이 성공적으로 생성되었습니다.",
                "orderId" to orderId,
                "orderDetails" to mapOf(
                    "userId" to orderRequest.userId,
                    "productId" to orderRequest.productId,
                    "productName" to orderRequest.productName,
                    "quantity" to orderRequest.quantity,
                    "unitPrice" to orderRequest.unitPrice,
                    "totalAmount" to orderRequest.unitPrice.multiply(BigDecimal(orderRequest.quantity))
                )
            )
            
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("주문 생성 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                mapOf(
                    "status" to "ERROR",
                    "message" to "주문 생성 중 오류가 발생했습니다: ${e.message}"
                )
            )
        }
    }

    /**
     * 주문 상태 업데이트 API
     * PUT /api/kafka/order/{orderId}/status
     */
    @PutMapping("/order/{orderId}/status")
    fun updateOrderStatus(
        @PathVariable orderId: String,
        @RequestParam status: String
    ): ResponseEntity<MessageResponse> {
        logger.info("🌐 API 요청: 주문 상태 업데이트 - orderId: $orderId, status: $status")
        
        return try {
            val orderStatus = OrderStatus.valueOf(status.uppercase())
            orderProducerService.updateOrderStatus(orderId, orderStatus)
            
            ResponseEntity.ok(
                MessageResponse(
                    status = "SUCCESS",
                    message = "주문 상태가 성공적으로 업데이트되었습니다."
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                MessageResponse(
                    status = "ERROR",
                    message = "잘못된 주문 상태입니다. 사용 가능한 상태: ${OrderStatus.values().joinToString(", ")}"
                )
            )
        } catch (e: Exception) {
            logger.error("주문 상태 업데이트 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                MessageResponse(
                    status = "ERROR",
                    message = "주문 상태 업데이트 중 오류가 발생했습니다: ${e.message}"
                )
            )
        }
    }

    /**
     * 헬스체크 API
     * GET /api/kafka/health
     */
    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "hello-kafka",
                "timestamp" to System.currentTimeMillis(),
                "message" to "Kafka 서비스가 정상적으로 동작 중입니다."
            )
        )
    }

    /**
     * 샘플 데이터 생성 API (테스트용)
     * POST /api/kafka/sample
     */
    @PostMapping("/sample")
    fun generateSampleData(): ResponseEntity<Map<String, Any>> {
        logger.info("🌐 API 요청: 샘플 데이터 생성")
        
        return try {
            // 샘플 메시지들 생성
            val messages = listOf("안녕하세요!", "Kafka 테스트", "샘플 메시지입니다.")
            messages.forEach { message ->
                messageProducerService.sendSimpleMessage(message)
            }

            // 샘플 주문들 생성
            val sampleOrders = listOf(
                OrderRequest("USER001", "PROD001", "아이폰 15", 1, BigDecimal("1200000")),
                OrderRequest("USER002", "PROD002", "갤럭시 S24", 2, BigDecimal("1100000")),
                OrderRequest("USER003", "PROD003", "맥북 프로", 1, BigDecimal("2500000"))
            )
            
            val orderIds = mutableListOf<String>()
            sampleOrders.forEach { orderRequest ->
                val orderId = orderProducerService.createOrder(orderRequest)
                orderIds.add(orderId)
            }

            ResponseEntity.ok(
                mapOf(
                    "status" to "SUCCESS",
                    "message" to "샘플 데이터가 성공적으로 생성되었습니다.",
                    "details" to mapOf(
                        "messagesCount" to messages.size,
                        "ordersCount" to sampleOrders.size,
                        "orderIds" to orderIds
                    )
                )
            )
            
        } catch (e: Exception) {
            logger.error("샘플 데이터 생성 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                mapOf(
                    "status" to "ERROR",
                    "message" to "샘플 데이터 생성 중 오류가 발생했습니다: ${e.message}"
                )
            )
        }
    }

    /**
     * 애플리케이션 정보 및 모니터링 링크
     * GET /api/kafka/info
     */
    @GetMapping("/info")
    fun getApplicationInfo(): ResponseEntity<Map<String, Any>> {
        val info = mapOf(
            "application" to "Hello Kafka",
            "description" to "Kafka 학습용 Spring Boot 애플리케이션",
            "monitoring" to mapOf(
                "actuator_health" to "http://localhost:8080/actuator/health",
                "actuator_metrics" to "http://localhost:8080/actuator/metrics",
                "actuator_info" to "http://localhost:8080/actuator/info",
                "kafka_ui" to "http://localhost:8090"
            ),
            "endpoints" to "/api/kafka/usage"
        )
        return ResponseEntity.ok(info)
    }

    /**
     * API 사용법 안내
     * GET /api/kafka/usage
     */
    @GetMapping("/usage")
    fun getUsageGuide(): ResponseEntity<Map<String, Any>> {
        val usageGuide = mapOf(
            "title" to "Kafka API 사용법 가이드",
            "endpoints" to listOf(
                mapOf(
                    "method" to "POST",
                    "path" to "/api/kafka/message",
                    "description" to "간단한 메시지 전송",
                    "body" to mapOf("message" to "전송할 메시지")
                ),
                mapOf(
                    "method" to "POST",
                    "path" to "/api/kafka/message/keyed?key=mykey",
                    "description" to "키-값 메시지 전송",
                    "body" to mapOf("message" to "전송할 메시지")
                ),
                mapOf(
                    "method" to "POST",
                    "path" to "/api/kafka/order",
                    "description" to "새로운 주문 생성",
                    "body" to mapOf(
                        "userId" to "USER001",
                        "productId" to "PROD001",
                        "productName" to "상품명",
                        "quantity" to 1,
                        "unitPrice" to 10000
                    )
                ),
                mapOf(
                    "method" to "PUT",
                    "path" to "/api/kafka/order/{orderId}/status?status=PAID",
                    "description" to "주문 상태 업데이트",
                    "parameters" to "status: CREATED, PAYMENT_PENDING, PAID, SHIPPED, DELIVERED, CANCELLED"
                ),
                mapOf(
                    "method" to "POST",
                    "path" to "/api/kafka/sample",
                    "description" to "샘플 데이터 생성 (테스트용)"
                ),
                mapOf(
                    "method" to "GET",
                    "path" to "/api/kafka/health",
                    "description" to "서비스 상태 확인"
                )
            ),
            "examples" to mapOf(
                "curl_message" to "curl -X POST http://localhost:8080/api/kafka/message -H 'Content-Type: application/json' -d '{\"message\": \"Hello Kafka!\"}'",
                "curl_order" to "curl -X POST http://localhost:8080/api/kafka/order -H 'Content-Type: application/json' -d '{\"userId\": \"USER001\", \"productId\": \"PROD001\", \"productName\": \"Test Product\", \"quantity\": 1, \"unitPrice\": 10000}'"
            )
        )
        
        return ResponseEntity.ok(usageGuide)
    }
}
