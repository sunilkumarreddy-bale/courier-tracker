package com.courier.tracker;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface UserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByEmail(String email);

  List<AppUser> findByRoleAndActiveTrueOrderByName(Role role);
}

interface ParcelRepository extends JpaRepository<Parcel, Long>, JpaSpecificationExecutor<Parcel> {
  Optional<Parcel> findByTrackingId(String trackingId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Parcel p where p.id=:id")
  Optional<Parcel> lockById(@Param("id") Long id);
}

interface EventRepository extends JpaRepository<ParcelEvent, Long> {
  List<ParcelEvent> findByParcelIdOrderByOccurredAtAscIdAsc(Long id);
}

interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findTop100ByUserIdOrderByCreatedAtDescIdDesc(Long id);

  List<Notification> findTop20ByEmailStatusInAndAttemptsLessThanOrderByCreatedAtAsc(
      List<String> states, int attempts);
}
