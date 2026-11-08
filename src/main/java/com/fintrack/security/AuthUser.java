package com.fintrack.security;

import com.fintrack.user.Role;

public record AuthUser(Long id, String email, Role role) {}
