package com.finguard.dto;
import com.finguard.entity.User;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserInfo {
    private UUID    id;
    private String  email;
    private String  fullName;
    private String  role;
    private Instant createdAt;
    public static UserInfo from(User u) {
        return UserInfo.builder()
                .id(u.getId()).email(u.getEmail())
                .fullName(u.getFullName()).role(u.getRole().name())
                .createdAt(u.getCreatedAt()).build();
    }
}
