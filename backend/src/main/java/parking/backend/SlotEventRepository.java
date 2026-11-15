package parking.backend;                                    // same package as the entity

import java.util.List;

import org.springframework.data.domain.Pageable;            // lets us limit how many rows come back
import org.springframework.data.jpa.repository.JpaRepository;   // gives save(), findAll(), etc. for free
import org.springframework.data.jpa.repository.Query;

public interface SlotEventRepository extends JpaRepository<SlotEvent, Long> {   // table type SlotEvent, primary key type Long

    // Spring builds the SQL from the method name: all rows, newest first (order by recordedAt descending)
    List<SlotEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);

    // Every change, oldest first: input for the analytics engine
    List<SlotEvent> findAllByOrderByRecordedAtAsc();

    // The newest row of each slot = the last known state of every slot
    @Query("select e from SlotEvent e where e.id in (select max(x.id) from SlotEvent x group by x.slotNumber) order by e.slotNumber")
    List<SlotEvent> findLatestPerSlot();
}
