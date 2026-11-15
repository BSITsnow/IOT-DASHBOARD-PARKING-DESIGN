package parking.backend;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {

    Optional<ParkingSession> findBySlotNumberAndEndedAtIsNull(int slotNumber);

    List<ParkingSession> findByEndedAtIsNull();

    List<ParkingSession> findByStartedAtGreaterThanEqual(Instant from);

    List<ParkingSession> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<ParkingSession> findBySlotNumberOrderByStartedAtDesc(int slotNumber, Pageable pageable);
}