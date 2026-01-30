package com.turnofacil.dto;

import com.turnofacil.model.enums.NotificationType;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private String link;
    private boolean read;
    private LocalDateTime createdAt;
    private String timeAgo;
    private String typeIcon;
    private String typeColor;
}
