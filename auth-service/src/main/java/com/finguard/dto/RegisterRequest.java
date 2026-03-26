package com.finguard.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank @Email               private String email;
    @NotBlank @Size(min = 8)       private String password;
    @NotBlank                      private String fullName;
}
