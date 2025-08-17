package com.ioob.hellokafka.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ioob.hellokafka.entity.NotificationEntity
import com.ioob.hellokafka.model.NotificationEvent
import com.ioob.hellokafka.model.NotificationPriority
import com.ioob.hellokafka.model.NotificationType
import com.ioob.hellokafka.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.util.*
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

/**
 * SSE 기반 실시간 알림 서비스
 */
@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)
    
    // 사용자별 SSE 연결 관리
    private val userEmitters = ConcurrentHashMap<String, MutableSet<SseEmitter>>()
    
    /**
     * 사용자의 SSE 연결 생성
     */
    fun createEmitterForUser(userId: String): SseEmitter {
        val emitter = SseEmitter(300_000L) // 5분 타임아웃
        
        // 사용자별 emitter 목록에 추가
        userEmitters.computeIfAbsent(userId) { mutableSetOf() }.add(emitter)
        
        // 연결 정리 이벤트 처리
        emitter.onCompletion {
            removeEmitter(userId, emitter)
            logger.info("👋 SSE 연결 완료: userId=$userId")
        }
        
        emitter.onTimeout {
            removeEmitter(userId, emitter)
            logger.info("⏰ SSE 연결 타임아웃: userId=$userId")
        }
        
        emitter.onError { 
            removeEmitter(userId, emitter)
            logger.warn("❌ SSE 연결 에러: userId=$userId, error=${it.message}")
        }
        
        // 연결 성공 메시지 전송
        try {
            emitter.send(
                SseEmitter.event()
                    .name("connected")
                    .data(mapOf(
                        "status" to "connected",
                        "message" to "알림 서비스에 연결되었습니다.",
                        "userId" to userId,
                        "timestamp" to System.currentTimeMillis()
                    ))
            )
            logger.info("✅ SSE 연결 성공: userId=$userId")
        } catch (e: Exception) {
            logger.error("SSE 초기 메시지 전송 실패: userId=$userId", e)
            removeEmitter(userId, emitter)
        }
        
        return emitter
    }
    
    /**
     * 알림 이벤트 발행 (Kafka로)
     */
    fun publishNotification(
        userId: String,
        type: NotificationType,
        title: String,
        message: String,
        relatedId: String? = null,
        priority: NotificationPriority = NotificationPriority.NORMAL
    ) {
        val notificationEvent = NotificationEvent(
            notificationId = "NOTIF-${UUID.randomUUID().toString().substring(0, 8)}",
            userId = userId,
            type = type,
            title = title,
            message = message,
            relatedId = relatedId,
            priority = priority
        )
        
        logger.info("📢 알림 이벤트 발행: userId=$userId, type=$type, message=$message")
        kafkaTemplate.send("notification-events", userId, notificationEvent)
    }
    
    /**
     * 실시간 알림 전송 (SSE로)
     */
    fun sendRealTimeNotification(notificationEvent: NotificationEvent) {
        // 1. DB에 저장
        val entity = NotificationEntity(
            notificationId = notificationEvent.notificationId,
            userId = notificationEvent.userId,
            type = notificationEvent.type,
            title = notificationEvent.title,
            message = notificationEvent.message,
            relatedId = notificationEvent.relatedId,
            priority = notificationEvent.priority
        )
        
        try {
            notificationRepository.save(entity)
            logger.info("💾 알림 DB 저장 완료: ${notificationEvent.notificationId}")
        } catch (e: Exception) {
            logger.error("❌ 알림 DB 저장 실패: ${notificationEvent.notificationId}", e)
        }
        
        // 2. SSE로 실시간 전송
        val emitters = userEmitters[notificationEvent.userId] ?: return
        val deadEmitters = mutableSetOf<SseEmitter>()
        
        emitters.forEach { emitter ->
            try {
                val sseData = mapOf(
                    "id" to notificationEvent.notificationId,
                    "type" to notificationEvent.type.name,
                    "title" to notificationEvent.title,
                    "message" to notificationEvent.message,
                    "relatedId" to notificationEvent.relatedId,
                    "priority" to notificationEvent.priority.name,
                    "timestamp" to System.currentTimeMillis()
                )
                
                emitter.send(
                    SseEmitter.event()
                        .name("notification")
                        .data(sseData)
                )
                
                logger.info("📱 실시간 알림 전송 성공: userId=${notificationEvent.userId}, type=${notificationEvent.type}")
                
            } catch (e: Exception) {
                logger.warn("📱 실시간 알림 전송 실패: userId=${notificationEvent.userId}", e)
                deadEmitters.add(emitter)
            }
        }
        
        // 죽은 연결 제거
        deadEmitters.forEach { deadEmitter ->
            removeEmitter(notificationEvent.userId, deadEmitter)
        }
    }
    
    /**
     * 사용자의 알림 목록 조회
     */
    fun getUserNotifications(userId: String): List<NotificationEntity> {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
    }
    
    /**
     * 사용자의 읽지 않은 알림 개수
     */
    fun getUnreadCount(userId: String): Long {
        return notificationRepository.countByUserIdAndIsReadFalse(userId)
    }
    
    /**
     * 알림 읽음 처리
     */
    @Transactional
    fun markAsRead(notificationId: String) {
        notificationRepository.markAsRead(notificationId, LocalDateTime.now())
        logger.info("📖 알림 읽음 처리: $notificationId")
    }
    
    /**
     * emitter 제거
     */
    private fun removeEmitter(userId: String, emitter: SseEmitter) {
        userEmitters[userId]?.remove(emitter)
        if (userEmitters[userId]?.isEmpty() == true) {
            userEmitters.remove(userId)
        }
    }
    
    /**
     * 현재 연결된 사용자 수
     */
    fun getConnectedUserCount(): Int {
        return userEmitters.size
    }
    
    /**
     * 사용자별 연결 수
     */
    fun getUserConnectionCount(userId: String): Int {
        return userEmitters[userId]?.size ?: 0
    }
}
