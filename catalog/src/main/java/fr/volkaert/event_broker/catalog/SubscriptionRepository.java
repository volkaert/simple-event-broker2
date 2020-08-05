package fr.volkaert.event_broker.catalog;

import fr.volkaert.event_broker.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Subscription findByCode(String code);
    void deleteByCode(String code);
}
