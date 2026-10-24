package kz.ielts.platform.repository;

import kz.ielts.platform.entity.SpeakingTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpeakingTopicRepository extends JpaRepository<SpeakingTopic, Long> {
    List<SpeakingTopic> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
