package fr.volkaert.event_broker.catalog;

import fr.volkaert.event_broker.model.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventTypeRepository extends JpaRepository<EventType, Long> {
    EventType findByCode(String code);
    void deleteByCode(String code);
}
