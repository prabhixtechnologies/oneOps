package com.prabhix.platform.user.web;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import com.prabhix.platform.user.dto.UserDtos.NotificationPrefsRequest;
import com.prabhix.platform.user.dto.UserDtos.UpdateProfileRequest;
import com.prabhix.platform.user.dto.UserDtos.UserProfile;
import com.prabhix.platform.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/users")
@RequiredArgsConstructor
public class UserController {

    private static final String IDENTITY_ACCOUNT =
            "Passwords and sessions are managed by Identity. Open the hosted account page.";

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

    /** Kept as 410 so old consoles get a stable "moved" rather than a silent 404. */
    @PostMapping("/me/password")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public void changePassword() {
        throw ApiException.of(ErrorCode.AUTH_MOVED_TO_IDENTITY, IDENTITY_ACCOUNT);
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

    /** Kept as 410 so old consoles get a stable "moved" rather than a silent 404. */
    @GetMapping("/me/sessions")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public void sessions() {
        throw ApiException.of(ErrorCode.AUTH_MOVED_TO_IDENTITY, IDENTITY_ACCOUNT);
    }

    @DeleteMapping("/me/sessions")
    @PreAuthorize(Authorize.AUTHENTICATED)
    public void revokeSession(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        userService.revokeSession(principal.userId(), id, principal.sessionId());
    }
}
