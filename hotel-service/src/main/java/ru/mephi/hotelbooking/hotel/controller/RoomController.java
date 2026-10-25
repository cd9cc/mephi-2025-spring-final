package ru.mephi.hotelbooking.hotel.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.hotel.dto.RoomRequest;
import ru.mephi.hotelbooking.hotel.dto.RoomResponse;
import ru.mephi.hotelbooking.hotel.dto.RoomSearch;
import ru.mephi.hotelbooking.hotel.service.RoomService;

@Tag(name = "Rooms")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "Free rooms, optionally for a period and a hotel, ordered by id (USER)")
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<RoomResponse> findFree(@Valid @ParameterObject RoomSearch search) {
        return roomService.findFree(search);
    }

    @Operation(summary = "Recommended free rooms: least booked first, then by id (USER)")
    @GetMapping("/recommend")
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_internal')")
    public List<RoomResponse> recommend(@Valid @ParameterObject RoomSearch search) {
        return roomService.recommend(search);
    }

    @Operation(summary = "Get a room (USER)")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public RoomResponse get(@PathVariable Long id) {
        return roomService.get(id);
    }

    @Operation(summary = "Add a room to a hotel (ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse create(@Valid @RequestBody RoomRequest request) {
        return roomService.create(request);
    }

    @Operation(summary = "Update a room, e.g. take it out of service with available=false (ADMIN)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return roomService.update(id, request);
    }

    @Operation(summary = "Delete a room that was never reserved (ADMIN)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }
}
