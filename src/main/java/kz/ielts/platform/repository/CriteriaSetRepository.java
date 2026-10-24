package kz.ielts.platform.repository;

import kz.ielts.platform.entity.CriteriaSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CriteriaSetRepository extends JpaRepository<CriteriaSet, Long> {
    List<CriteriaSet> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<CriteriaSet> findFirstByOwnerIdAndActiveTrueOrderByVersionDesc(Long ownerId);
}
