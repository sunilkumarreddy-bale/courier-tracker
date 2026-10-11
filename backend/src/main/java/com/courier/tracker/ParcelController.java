package com.courier.tracker;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class ParcelController {
  private final ParcelService service;

  ParcelController(ParcelService s) {
    service = s;
  }

  private AppUser user(Authentication a) {
    return service.user(a.getName());
  }

  @GetMapping("/tracking/{trackingId}")
  Dtos.PublicTracking track(@PathVariable String trackingId) {
    return service.track(trackingId);
  }

  @GetMapping("/parcels")
  Page<Dtos.ParcelView> list(
      Authentication a,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) Status status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(user(a), search, status, page, size);
  }

  @GetMapping("/parcels/{id}")
  Dtos.ParcelDetail detail(@PathVariable Long id, Authentication a) {
    return service.detail(id, user(a));
  }

  @PostMapping("/parcels")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  Dtos.ParcelView create(@Valid @RequestBody Dtos.CreateParcel d, Authentication a) {
    return service.create(d, user(a));
  }

  @PatchMapping("/parcels/{id}/assignment")
  Dtos.ParcelView assign(
      @PathVariable Long id, @Valid @RequestBody Dtos.Assignment d, Authentication a) {
    return service.assign(id, d.agentId(), user(a));
  }

  @PatchMapping("/parcels/{id}/status")
  Dtos.ParcelView status(
      @PathVariable Long id, @Valid @RequestBody Dtos.StatusChange d, Authentication a) {
    return service.update(id, d, user(a));
  }

  @GetMapping("/agents")
  List<Dtos.UserView> agents(Authentication a) {
    return service.agents(user(a));
  }

  @PostMapping("/agents")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  Dtos.UserView createAgent(@Valid @RequestBody Dtos.CreateAgent d, Authentication a) {
    return service.createAgent(d, user(a));
  }

  @GetMapping("/dashboard")
  Dtos.Dashboard dashboard(Authentication a) {
    return service.dashboard(user(a));
  }

  @GetMapping("/notifications")
  List<Dtos.NotificationView> inbox(Authentication a) {
    return service.inbox(user(a));
  }

  @PatchMapping("/notifications/{id}/read")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  void read(@PathVariable Long id, Authentication a) {
    service.read(id, user(a));
  }
}
