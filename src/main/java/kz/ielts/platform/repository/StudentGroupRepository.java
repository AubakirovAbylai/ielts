package kz.ielts.platform.repository;

import kz.ielts.platform.entity.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {
    List<StudentGroup> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
}
