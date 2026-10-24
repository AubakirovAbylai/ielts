package kz.ielts.platform.repository;

import kz.ielts.platform.entity.Essay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EssayRepository extends JpaRepository<Essay, Long> {
    List<Essay> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<Essay> findByStudentTeacherId(Long teacherId);
    List<Essay> findByStudentIdAndTaskIdOrderByAttemptDesc(Long studentId, Long taskId);
    Optional<Essay> findFirstByStudentIdAndTaskIdOrderByAttemptDesc(Long studentId, Long taskId);
}
