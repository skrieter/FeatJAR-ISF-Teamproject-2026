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

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.featjar.AnalysisTest;
import de.featjar.analysis.sat4j.computation.ComputeSatisfiableSAT4J;
import de.featjar.base.computation.Computations;
import de.featjar.formula.assignment.conversion.ComputeBooleanClauseList;
import de.featjar.formula.computation.ComputeCNFFormula;
import de.featjar.formula.computation.ComputeNNFFormula;
import de.featjar.formula.io.textual.JavaSymbols;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.And;
import de.featjar.formula.structure.connective.Reference;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Uses the GPL feature model: Directed/Undirected and Weighted/Unweighted are alternatives, Base is mandatory.
 */
public class DeadCodeTest extends AnalysisTest {

    @Test
    public void contradictionIsDead() {
        assertEquals(List.of("Dead code at lines 2-2: A && !A"), dead("//#if A && !A", "a();", "//#endif"));
    }

    @Test
    public void alternativesTogetherAreDead() {
        assertEquals(
                List.of("Dead code at lines 2-3: Directed && Undirected"),
                dead("//#if Directed && Undirected", "a();", "b();", "//#endif"));
    }

    @Test
    public void elseOfMandatoryFeatureIsDead() {
        assertEquals(
                List.of("Dead code at lines 4-4: !Base"), dead("//#if Base", "a();", "//#else", "b();", "//#endif"));
    }

    @Test
    public void elseAfterAllAlternativesIsDead() {
        assertEquals(
                List.of("Dead code at lines 6-6: !Weighted && !Unweighted"),
                dead("//#if Weighted", "a();", "//#elif Unweighted", "b();", "//#else", "c();", "//#endif"));
    }

    @Test
    public void nestedBlockIsDeadBecauseOfOuterCondition() {
        assertEquals(
                List.of("Dead code at lines 4-4: Directed && Undirected"),
                dead("//#if Directed", "a();", "//#if Undirected", "b();", "//#endif", "c();", "//#endif"));
    }

    @Test
    public void satisfiableBlocksAreNotDead() {
        assertEquals(
                List.of(),
                dead("x();", "//#if BFS || DFS", "a();", "//#elif !Base", "//#endif", "//#if A", "b();", "//#endif"));
    }

    private static List<String> dead(String... lines) {
        return new PreprocessorAnalyzer("//#", JavaSymbols.INSTANCE)
                .findDeadCode(Stream.of(lines), consistentWith(loadFormula("GPL/model.xml")));
    }

    /**
     * {@return a test whether a presence condition can be true in some configuration of the feature model}
     */
    private static Predicate<IFormula> consistentWith(IFormula featureModel) {
        IFormula model = featureModel instanceof Reference reference ? reference.getExpression() : featureModel;
        return condition -> Computations.of((IFormula) new And(model, condition))
                .map(ComputeNNFFormula::new)
                .map(ComputeCNFFormula::new)
                .map(ComputeBooleanClauseList::new)
                .map(ComputeSatisfiableSAT4J::new)
                .compute();
    }
}
