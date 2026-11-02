package kz.ielts.platform.repository;

import kz.ielts.platform.entity.CriteriaSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CriteriaSetRepository extends JpaRepository<CriteriaSet, Long> {
    List<CriteriaSet> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<CriteriaSet> findFirstByOwnerIdAndActiveTrueOrderByIdDesc(Long ownerId);
    Optional<CriteriaSet> findFirstByOwnerIdAndActiveTrueAndTaskTypeOrderByIdDesc(Long ownerId, String taskType);
    Optional<CriteriaSet> findFirstByOwnerIdAndActiveTrueAndTaskTypeIsNullOrderByIdDesc(Long ownerId);

    /**
     * Активный набор для конкретного задания: сначала набор именно для TASK_1/TASK_2,
     * затем общий (taskType = null), затем любой активный (старые данные).
     */
    default Optional<CriteriaSet> findActiveFor(Long ownerId, String taskType) {
        if (taskType != null) {
            Optional<CriteriaSet> exact = findFirstByOwnerIdAndActiveTrueAndTaskTypeOrderByIdDesc(ownerId, taskType);
            if (exact != null && exact.isPresent()) return exact;
            Optional<CriteriaSet> generic = findFirstByOwnerIdAndActiveTrueAndTaskTypeIsNullOrderByIdDesc(ownerId);
            if (generic != null && generic.isPresent()) return generic;
        }
        Optional<CriteriaSet> any = findFirstByOwnerIdAndActiveTrueOrderByIdDesc(ownerId);
        return any == null ? Optional.empty() : any;
    }
}
