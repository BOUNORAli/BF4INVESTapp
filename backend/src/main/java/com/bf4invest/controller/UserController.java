package com.bf4invest.controller;

import com.bf4invest.model.User;
import com.bf4invest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll();
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable String id) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setPassword(null); // Ne pas exposer le mot de passe
                    return ResponseEntity.ok(user);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        
        User created = userRepository.save(user);
        created.setPassword(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User user) {
        return userRepository.findById(id)
                .map(existing -> {
                    existing.setName(user.getName());
                    existing.setEmail(user.getEmail());
                    if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                        existing.setPassword(passwordEncoder.encode(user.getPassword()));
                    }
                    existing.setRole(user.getRole());
                    existing.setEnabled(user.isEnabled());
                    existing.setUpdatedAt(LocalDateTime.now());
                    
                    User updated = userRepository.save(existing);
                    updated.setPassword(null);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        User target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        String currentEmail = getCurrentUserEmail();
        if (currentEmail != null && currentEmail.equals(target.getEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (target.getRole() == User.Role.ADMIN && userRepository.countByRole(User.Role.ADMIN) <= 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : null;
    }
}




