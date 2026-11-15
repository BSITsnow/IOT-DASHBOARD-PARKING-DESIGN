package parking.backend;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parking_session")
public class ParkingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int slotNumber;
    private Instant startedAt;
    private Instant endedAt;          // null = car is still parked

    protected ParkingSession() { }

    public ParkingSession(int slotNumber, Instant startedAt) {
        this.slotNumber = slotNumber;
        this.startedAt = startedAt;
    }

    public void close(Instant endedAt) { this.endedAt = endedAt; }

    public Long getId() { return id; }
    public int getSlotNumber() { return slotNumber; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
}