package com.ioob.hellokafka.entity

import com.ioob.hellokafka.model.NotificationPriority
import com.ioob.hellokafka.model.NotificationType
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 알림 엔티티 (PostgreSQL 저장용)
 */
@Entity
@Table(name = "notifications")
data class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "notification_id", unique = true, nullable = false)
    val notificationId: String,
    
    @Column(name = "user_id", nullable = false)
    val userId: String,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: NotificationType,
    
    @Column(name = "title", nullable = false)
    val title: String,
    
    @Column(name = "message", nullable = false, length = 1000)
    val message: String,
    
    @Column(name = "related_id")
    val relatedId: String? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    val priority: NotificationPriority,
    
    @Column(name = "is_read", nullable = false)
    val isRead: Boolean = false,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "read_at")
    val readAt: LocalDateTime? = null
) {
    // JPA를 위한 기본 생성자
    constructor() : this(
        notificationId = "",
        userId = "",
        type = NotificationType.SYSTEM_ALERT,
        title = "",
        message = "",
        priority = NotificationPriority.NORMAL
    )
}
