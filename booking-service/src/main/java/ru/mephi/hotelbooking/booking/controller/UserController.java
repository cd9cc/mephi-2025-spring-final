package ru.mephi.hotelbooking.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.booking.dto.user.UserCreateRequest;
import ru.mephi.hotelbooking.booking.dto.user.UserResponse;
import ru.mephi.hotelbooking.booking.dto.user.UserUpdateRequest;
import ru.mephi.hotelbooking.booking.service.UserService;
import ru.mephi.hotelbooking.common.dto.PageResponse;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/bookings/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @Operation(summary = "List users (ADMIN)")
    @GetMapping
    public PageResponse<UserResponse> list(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return PageResponse.of(userService.list(pageable));
    }

    @Operation(summary = "Get a user (ADMIN)")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @Operation(summary = "Create a user with any role (ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @Operation(summary = "Update a user: only the given fields change (ADMIN)")
    @PatchMapping
    public UserResponse update(@Valid @RequestBody UserUpdateRequest request) {
        return userService.update(request);
    }

    @Operation(summary = "Delete a user without active bookings, with the booking history (ADMIN)")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam Long id) {
        userService.delete(id);
    }
}
