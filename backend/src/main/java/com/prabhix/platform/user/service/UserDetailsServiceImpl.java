package com.prabhix.platform.user.service;

import com.prabhix.platform.user.domain.User;
import com.prabhix.platform.user.domain.User.UserStatus;
import com.prabhix.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(username)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        boolean enabled = user.getStatus() != UserStatus.DISABLED;
        boolean locked = user.isLockedNow() || user.getStatus() == UserStatus.LOCKED;

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .disabled(!enabled)
                .accountLocked(locked)
                .authorities(List.of(new SimpleGrantedAuthority("USER")))
                .build();
    }
}
