package com.ioob.hellokafka.repository

import com.ioob.hellokafka.entity.NotificationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface NotificationRepository : JpaRepository<NotificationEntity, Long> {
    
    /**
     * 사용자별 알림 조회 (최신순)
     */
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<NotificationEntity>
    
    /**
     * 사용자별 읽지 않은 알림 조회
     */
    fun findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId: String): List<NotificationEntity>
    
    /**
     * 사용자별 읽지 않은 알림 개수
     */
    fun countByUserIdAndIsReadFalse(userId: String): Long
    
    /**
     * 알림을 읽음 처리
     */
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true, n.readAt = :readAt WHERE n.notificationId = :notificationId")
    fun markAsRead(notificationId: String, readAt: LocalDateTime)
    
    /**
     * 사용자의 모든 알림을 읽음 처리
     */
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    fun markAllAsReadByUserId(userId: String, readAt: LocalDateTime)
    
    /**
     * 특정 관련 ID의 알림 조회 (주문 ID별 알림 추적용)
     */
    fun findByRelatedIdOrderByCreatedAtAsc(relatedId: String): List<NotificationEntity>
}
