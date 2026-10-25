package ru.mephi.hotelbooking.hotel.controller;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mephi.hotelbooking.common.dto.PageResponse;
import ru.mephi.hotelbooking.hotel.dto.HotelFilter;
import ru.mephi.hotelbooking.hotel.dto.HotelRequest;
import ru.mephi.hotelbooking.hotel.dto.HotelResponse;
import ru.mephi.hotelbooking.hotel.service.HotelService;

@Tag(name = "Hotels")
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @Operation(summary = "List hotels with optional city/name filters, paged and sortable (USER)")
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public PageResponse<HotelResponse> list(@ParameterObject HotelFilter filter,
                                            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return PageResponse.of(hotelService.search(filter, pageable));
    }

    @Operation(summary = "Get a hotel (USER)")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public HotelResponse get(@PathVariable Long id) {
        return hotelService.get(id);
    }

    @Operation(summary = "Add a hotel (ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public HotelResponse create(@Valid @RequestBody HotelRequest request) {
        return hotelService.create(request);
    }

    @Operation(summary = "Update a hotel (ADMIN)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelResponse update(@PathVariable Long id, @Valid @RequestBody HotelRequest request) {
        return hotelService.update(id, request);
    }

    @Operation(summary = "Delete a hotel without reservations (ADMIN)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        hotelService.delete(id);
    }
}
