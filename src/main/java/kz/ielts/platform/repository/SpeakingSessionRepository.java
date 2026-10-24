package kz.ielts.platform.repository;

import kz.ielts.platform.entity.SpeakingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpeakingSessionRepository extends JpaRepository<SpeakingSession, Long> {
    List<SpeakingSession> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<SpeakingSession> findByStudentTeacherId(Long teacherId);
}
