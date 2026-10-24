package kz.ielts.platform.repository;

import kz.ielts.platform.entity.EssayCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EssayCheckRepository extends JpaRepository<EssayCheck, Long> {
    Optional<EssayCheck> findByEssayId(Long essayId);
    List<EssayCheck> findByEssayStudentTeacherId(Long teacherId);
}
