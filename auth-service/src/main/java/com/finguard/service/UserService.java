package com.finguard.service;

import com.finguard.dto.ChangePasswordRequest;
import com.finguard.dto.UserInfo;
import com.finguard.entity.User;
import com.finguard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository repo;
    private final PasswordEncoder encoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return repo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user: " + email));
    }

    public UserInfo getProfile(UUID userId) {
        return UserInfo.from(find(userId));
    }

    @Transactional
    public UserInfo updateFullName(UUID userId, String name) {
        User u = find(userId);
        u.setFullName(name);
        return UserInfo.from(repo.save(u));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User u = find(userId);
        if (!encoder.matches(req.getCurrentPassword(), u.getPassword()))
            throw new BadCredentialsException("Current password is incorrect");
        u.setPassword(encoder.encode(req.getNewPassword()));
        repo.save(u);
    }

    public List<UserInfo> getAllUsers() {
        return repo.findAll().stream().map(UserInfo::from).collect(Collectors.toList());
    }

    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        User u = find(userId);
        u.setEnabled(enabled);
        repo.save(u);
    }

    @Transactional
    public void promoteToAdmin(UUID userId) {
        User u = find(userId);
        u.setRole(User.Role.ADMIN);
        repo.save(u);
    }

    private User find(UUID id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("User not found: " + id));
    }
}
