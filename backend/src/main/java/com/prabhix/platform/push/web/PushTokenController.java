package com.prabhix.platform.push.web;

import com.prabhix.platform.push.dto.PushDtos;
import com.prabhix.platform.push.service.PushTokenService;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Push devices", description = "Mobile push notification device registration")
@RestController
@RequestMapping("/api/v1/oneops/devices/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService tokenService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public PushDtos.RegisterPushTokenResponse register(@CurrentUser PrabhixPrincipal principal,
                                                       @Valid @RequestBody PushDtos.RegisterPushTokenRequest request) {
        return tokenService.register(principal, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deregister(@CurrentUser PrabhixPrincipal principal, @RequestParam String token) {
        tokenService.deregister(principal, token);
    }

    @GetMapping
    public List<PushDtos.DeviceView> list(@CurrentUser PrabhixPrincipal principal) {
        return tokenService.listMine(principal);
    }
}
