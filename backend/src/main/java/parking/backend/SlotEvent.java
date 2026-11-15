package parking.backend;                                    // same package as the other backend classes

import java.time.Instant;                               // JPA annotations: Entity, Id, GeneratedValue, etc.

import jakarta.persistence.Entity;                                   // an exact moment in time
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity                                                     // marks this class as a database table
@Table(name = "slot_event")                                 // the table will be called slot_event
public class SlotEvent {

    @Id                                                     // this field is the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)     // the database fills in 1, 2, 3, ... automatically
    private Long id;

    private int slotNumber;                                 // 1 means P1, 2 means P2, same order as lot-layout.txt
    private boolean occupied;                               // true if the slot became occupied, false if it became vacant
    private Instant recordedAt;                             // when the change was received

    protected SlotEvent() { }                               // JPA requires an empty constructor to build objects from rows

    public SlotEvent(int slotNumber, boolean occupied, Instant recordedAt) {   // constructor we use in our own code
        this.slotNumber = slotNumber;                       // store the slot number
        this.occupied = occupied;                           // store the new status
        this.recordedAt = recordedAt;                       // store the time
    }

    public Long getId() { return id; }                      // getters let Spring convert the object to JSON for the API
    public int getSlotNumber() { return slotNumber; }
    public boolean isOccupied() { return occupied; }
    public Instant getRecordedAt() { return recordedAt; }
}