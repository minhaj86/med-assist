package com.medassist.diagnosis.scoring;

import com.medassist.diagnosis.context.ClinicalContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Runs all registered score calculators; a failing calculator never prevents the others from running. */
@Service
public class ClinicalScoringService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalScoringService.class);

    private final List<ClinicalScoreCalculator> calculators;

    public ClinicalScoringService(List<ClinicalScoreCalculator> calculators) {
        this.calculators = calculators.stream().sorted(Comparator.comparing(ClinicalScoreCalculator::scoreId)).toList();
    }

    public List<ScoreResult> calculateAll(ClinicalContext context) {
        List<ScoreResult> results = new ArrayList<>();
        for (ClinicalScoreCalculator calculator : calculators) {
            try {
                results.add(calculator.calculate(context));
            } catch (RuntimeException e) {
                log.error("Score calculator {} failed", calculator.scoreId(), e);
            }
        }
        return List.copyOf(results);
    }
}
