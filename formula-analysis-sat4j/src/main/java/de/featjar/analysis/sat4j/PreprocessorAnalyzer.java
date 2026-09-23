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

import de.featjar.base.computation.Computations;
import de.featjar.base.tree.Trees;
import de.featjar.composition.Preprocessor;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentList;
import de.featjar.formula.assignment.conversion.ComputeBooleanClauseList;
import de.featjar.formula.computation.ComputeCNFFormula;
import de.featjar.formula.computation.ComputeNNFFormula;
import de.featjar.formula.io.textual.ExpressionSerializer;
import de.featjar.formula.io.textual.Symbols;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.And;
import de.featjar.formula.structure.connective.Reference;
import de.featjar.formula.structure.predicate.False;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

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
        IFormula model = featureModel instanceof Reference ref ? ref.getExpression() : featureModel;
        List<IFormula> presence = computePresenceConditions(lines);
        ExpressionSerializer serializer = new ExpressionSerializer();
        serializer.setSymbols(getSymbols());
        List<String> dead = new ArrayList<>();
        // a code block is a maximal run of lines between annotations, which have the presence condition False
        for (int start = 0, i = 0; i <= presence.size(); i++) {
            if (i == presence.size() || presence.get(i) == False.INSTANCE) {
                if (start < i && !isSatisfiable(new And(model, presence.get(start)))) {
                    dead.add(String.format(
                            "Dead code at lines %d-%d: %s",
                            start + 1,
                            i,
                            Trees.traverse(presence.get(start), serializer).orElseThrow()));
                }
                start = i + 1;
            }
        }
        return dead;
    }

    /**
     * {@return whether the given formula has a satisfying assignment}
     */
    private static boolean isSatisfiable(IFormula formula) {
        BooleanAssignmentList clauses = Computations.of(formula)
                .map(ComputeNNFFormula::new)
                .map(ComputeCNFFormula::new)
                .map(ComputeBooleanClauseList::new)
                .compute();

        ISolver solver = SolverFactory.newDefault();
        solver.newVar(clauses.getVariableMap().size());
        try {
            for (BooleanAssignment clause : clauses) {
                solver.addClause(new VecInt(clause.get()));
            }
            return solver.isSatisfiable();
        } catch (ContradictionException | TimeoutException e) {
            return false;
        }
    }
}