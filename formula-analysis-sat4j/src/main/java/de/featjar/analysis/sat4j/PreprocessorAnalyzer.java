/*
 * Copyright (C) 2026 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula-analysis-sat4j.
 *
 * formula-analysis-sat4j is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula-analysis-sat4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula-analysis-sat4j. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula-analysis-sat4j> for further information.
 */
package de.featjar.analysis.sat4j;

import de.featjar.analysis.sat4j.computation.ComputeSatisfiableSAT4J;
import de.featjar.base.computation.Computations;
import de.featjar.base.tree.Trees;
import de.featjar.composition.Preprocessor;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignmentList;
import de.featjar.formula.assignment.conversion.ComputeBooleanClauseList;
import de.featjar.formula.computation.ComputeCNFFormula;
import de.featjar.formula.computation.ComputeNNFFormula;
import de.featjar.formula.io.textual.ExpressionSerializer;
import de.featjar.formula.io.textual.Symbols;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.predicate.False;
import de.featjar.formula.structure.predicate.True;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class PreprocessorAnalyzer extends Preprocessor {

    public PreprocessorAnalyzer(String annotationPrefix, Symbols symbols) {
        super(annotationPrefix, symbols);
    }

    /**
     * {@return one message per block of code lines whose presence condition is not satisfiable
     * together with the given feature model}
     *
     * @param lines the line stream
     * @param featureModel the feature model
     */
    public List<String> findDeadCode(Stream<String> lines, IFormula featureModel) {
        List<IFormula> presence = computePresenceConditions(lines);
        ExpressionSerializer serializer = new ExpressionSerializer();
        serializer.setSymbols(getSymbols());
        List<String> dead = new ArrayList<>();

        // Compute the CNF of the feature model once (ComputeNNFFORMULA deals with the reference)
        BooleanAssignmentList modelClauses = Computations.of(featureModel)
                .map(ComputeNNFFormula::new)
                .map(ComputeCNFFormula::new)
                .map(ComputeBooleanClauseList::new)
                .compute();

        // a code block is a maximal run of lines between annotations, which have the presence condition False
        for (int start = 0, i = 0; i <= presence.size(); i++) {
            if (i == presence.size() || presence.get(i) == False.INSTANCE) {
                if (start < i) {
                    IFormula presenceCondition = presence.get(start);
                    if (!isSatisfiable(modelClauses, presenceCondition)) {
                        dead.add(String.format(
                                "Dead code at lines %d-%d: %s",
                                start + 1,
                                i,
                                Trees.traverse(presenceCondition, serializer).orElseThrow()));
                    }
                }
                start = i + 1;
            }
        }
        return dead;
    }

    /**
     * {@return whether the given presence condition is satisfiable together with the model}
     *
     * Uses {@link ComputeSatisfiableSAT4J} with the model's clause list as the base
     * and the presence condition's clause list as an assumed clause list.
     * This avoids recomputing the CNF of the model for every block.
     */
    private static boolean isSatisfiable(BooleanAssignmentList modelClauses, IFormula presenceCondition) {
        // A True condition is always satisfiable (assuming the model itself is satisfiable)
        if (presenceCondition == True.INSTANCE) {
            return true;
        }
        // A False condition is never satisfiable
        if (presenceCondition == False.INSTANCE) {
            return false;
        }

        // Compute the CNF of the presence condition
        BooleanAssignmentList presenceClauses = Computations.of(presenceCondition)
                .map(ComputeNNFFormula::new)
                .map(ComputeCNFFormula::new)
                .map(ComputeBooleanClauseList::new)
                .compute();

        // Merge variable maps so that both clause lists use the same indices
        VariableMap mergedMap = new VariableMap(modelClauses.getVariableMap(), presenceClauses.getVariableMap());
        BooleanAssignmentList remappedModelClauses = modelClauses.remap(mergedMap, false);
        BooleanAssignmentList remappedPresenceClauses = presenceClauses.remap(mergedMap, false);

        // Use ComputeSatisfiableSAT4J directly with the assumed clause list
        return Computations.of(remappedModelClauses)
                .map(ComputeSatisfiableSAT4J::new)
                .set(ComputeSatisfiableSAT4J.ASSUMED_CLAUSE_LIST, remappedPresenceClauses)
                .compute();
    }
}
