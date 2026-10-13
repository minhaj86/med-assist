package com.medassist.clinicalrules.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, UUID> {

    @Query("""
            select d from RuleDefinition d
            where d.effectiveFrom <= :at and (d.effectiveUntil is null or d.effectiveUntil > :at)
            order by d.ruleId, d.effectiveFrom desc
            """)
    List<RuleDefinition> findEffectiveAt(@Param("at") Instant at);

    List<RuleDefinition> findByRuleIdOrderByEffectiveFromDesc(String ruleId);

    List<RuleDefinition> findAllByOrderByRuleIdAscEffectiveFromDesc();

    boolean existsByRuleIdAndVersion(String ruleId, String version);
}
