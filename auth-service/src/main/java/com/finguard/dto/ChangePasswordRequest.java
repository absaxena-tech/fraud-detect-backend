package com.finguard.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class ChangePasswordRequest {
    @NotBlank                   private String currentPassword;
    @NotBlank @Size(min = 8)    private String newPassword;
}
