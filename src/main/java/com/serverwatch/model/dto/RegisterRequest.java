package com.serverwatch.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        String displayName,
        List<String> permissions   // optional; null → default view-only set
) {}
