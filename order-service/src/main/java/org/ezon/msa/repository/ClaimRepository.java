package org.ezon.msa.repository;

import java.util.List;

import org.ezon.msa.entity.Claim;
import org.ezon.msa.enums.ClaimType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
	List<Claim> findByUserId(Long userId);

	List<Claim> findByOrderItemIdAndType(Long orderItemId, ClaimType type);
}
