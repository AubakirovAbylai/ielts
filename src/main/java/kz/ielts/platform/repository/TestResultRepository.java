package kz.ielts.platform.repository;

import kz.ielts.platform.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findByStudentIdOrderByTestDateAscIdAsc(Long studentId);
    List<TestResult> findByStudentTeacherIdOrderByTestDateDesc(Long teacherId);
}
