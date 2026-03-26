package com.finguard.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class LoginRequest {
    @NotBlank @Email   private String email;
    @NotBlank          private String password;
}
