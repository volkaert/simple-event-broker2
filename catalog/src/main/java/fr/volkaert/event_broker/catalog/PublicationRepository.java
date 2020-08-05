package fr.volkaert.event_broker.catalog;

import fr.volkaert.event_broker.model.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {
    Publication findByCode(String code);
    void deleteByCode(String code);
}
