package com.prabhix.platform.user.web;

import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import com.prabhix.platform.user.dto.UserDtos.ChangePasswordRequest;
import com.prabhix.platform.user.dto.UserDtos.NotificationPrefsRequest;
import com.prabhix.platform.user.dto.UserDtos.UpdateProfileRequest;
import com.prabhix.platform.user.dto.UserDtos.UserProfile;
import com.prabhix.platform.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public UserProfile me(@CurrentUser PrabhixPrincipal principal) {
        return userService.getProfile(principal.userId());
    }

    @PatchMapping("/me")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public UserProfile updateMe(@CurrentUser PrabhixPrincipal principal,
                                @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.userId(), request);
    }

    @PostMapping("/me/password")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public void changePassword(@CurrentUser PrabhixPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.userId(), request);
    }

    @PatchMapping("/me/notification-prefs")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public UserProfile updateNotificationPrefs(@CurrentUser PrabhixPrincipal principal,
                                               @Valid @RequestBody NotificationPrefsRequest request) {
        return userService.updateNotificationPrefs(principal.userId(), request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Authorize.AUTHENTICATED)
    public UserProfile uploadAvatar(@CurrentUser PrabhixPrincipal principal,
                                    @RequestPart("file") MultipartFile file) throws IOException {
        return userService.updateAvatar(principal.userId(),
                file.getBytes(), file.getOriginalFilename(), file.getContentType());
    }

    @GetMapping("/me/sessions")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public List<com.prabhix.platform.user.dto.UserDtos.DeviceSessionView> sessions(
            @CurrentUser PrabhixPrincipal principal) {
        return userService.listSessions(principal.userId(), principal.sessionId());
    }

    @DeleteMapping("/me/sessions/{id}")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public void revokeSession(@CurrentUser PrabhixPrincipal principal, @PathVariable UUID id) {
        userService.revokeSession(principal.userId(), id, principal.sessionId());
    }
}
