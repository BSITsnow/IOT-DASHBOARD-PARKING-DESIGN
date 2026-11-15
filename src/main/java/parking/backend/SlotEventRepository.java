package parking.backend;
                            // same package as the entity
import org.springframework.data.domain.Pageable;            // lets us limit how many rows come back
import org.springframework.data.jpa.repository.JpaRepository;   // gives save(), findAll(), etc. for free
import java.util.List;

public interface SlotEventRepository extends JpaRepository<SlotEvent, Long> {   // table type SlotEvent, primary key type Long

    // Spring builds the SQL from the method name: all rows, newest first (order by recordedAt descending)
    List<SlotEvent> findAllByOrderByRecordedAtDesc(Pageable pageable);
}