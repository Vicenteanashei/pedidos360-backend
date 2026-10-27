package com.pedidos360.catalog;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reservation r where r.id=:id")
  Optional<Reservation> lock(String id);
}
