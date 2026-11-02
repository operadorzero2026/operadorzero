package br.com.operadorzero.notification;

import br.com.operadorzero.identity.AuthenticatedUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class NotificationController {
    private final NotificationService notifications;
    private final AchievementService achievements;

    public NotificationController(NotificationService notifications, AchievementService achievements) {
        this.notifications = notifications;
        this.achievements = achievements;
    }

    @GetMapping("/notifications")
    NotificationService.NotificationPage notifications(@AuthenticationPrincipal AuthenticatedUser user) {
        return notifications.list(user);
    }

    @GetMapping("/notifications/unread")
    Map<String, Long> unread(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("unread", notifications.unread(user));
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        notifications.markRead(user, id);
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void readAll(@AuthenticationPrincipal AuthenticatedUser user) {
        notifications.markAllRead(user);
    }

    @GetMapping("/achievements")
    AchievementService.AchievementPage achievements(@AuthenticationPrincipal AuthenticatedUser user) {
        return achievements.mine(user);
    }
}
