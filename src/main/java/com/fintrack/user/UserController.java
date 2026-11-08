package com.fintrack.user;

import com.fintrack.common.ResourceNotFoundException;
import com.fintrack.security.AuthUser;
import com.fintrack.user.AuthDtos.UpdateProfileRequest;
import com.fintrack.user.AuthDtos.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository users;

    @GetMapping("/profile")
    public UserDto profile(@AuthenticationPrincipal AuthUser me) {
        return UserDto.from(load(me));
    }

    @PutMapping("/profile")
    @Transactional
    public UserDto update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest r) {
        User u = load(me);
        u.setFullName(r.fullName().trim());
        return UserDto.from(u);
    }

    private User load(AuthUser me) {
        return users.findById(me.id()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
