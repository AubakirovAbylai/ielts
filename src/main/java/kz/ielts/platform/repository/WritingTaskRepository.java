package kz.ielts.platform.repository;

import kz.ielts.platform.entity.WritingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WritingTaskRepository extends JpaRepository<WritingTask, Long> {
    List<WritingTask> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
