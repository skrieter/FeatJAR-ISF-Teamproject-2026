/*
 * Copyright (C) 2026 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-feature-model-assistance.
 *
 * feature-model-assistance is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * feature-model-assistance is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with feature-model-assistance. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-feature-model-assistance> for further information.
 */
package de.featjar.featureide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.featjar.feature.configuration.Configuration;
import de.featjar.feature.model.IFeature;
import java.util.List;
import org.junit.jupiter.api.Test;

public class FeatureModelAnalyzerTest {

    @Test
    public void randomConfigurationsWithSat4j() {
        FeatJARWrapper wrapper = new FeatJARWrapper();
        FeatureModelBuilder builder = wrapper.featureModelBuilder();

        IFeature root = builder.addRoot("Root");
        IFeature optional = builder.addFeatureBelow("Optional", root);
        builder.setFeatureToOptional(optional);

        FeatureModelAnalyzer analyzer =
                wrapper.featureModelAnalyzer(builder.getFeatureModel());

        List<Configuration> configurations =
                analyzer.randomConfigurations(10, 1L, "sat4j").orElseThrow();

        assertEquals(10, configurations.size());

        for (Configuration configuration : configurations) {
            assertTrue(configuration.getSelected().contains("Root"));
        }
    }

    @Test
    public void randomConfigurationsWithDdnnife() {
        FeatJARWrapper wrapper = new FeatJARWrapper();
        FeatureModelBuilder builder = wrapper.featureModelBuilder();

        IFeature root = builder.addRoot("Root");
        IFeature optional = builder.addFeatureBelow("Optional", root);
        builder.setFeatureToOptional(optional);

        FeatureModelAnalyzer analyzer =
                wrapper.featureModelAnalyzer(builder.getFeatureModel());

        List<Configuration> configurations =
                analyzer.randomConfigurations(10, 1L, "ddnnife").orElseThrow();

        assertEquals(10, configurations.size());

        for (Configuration configuration : configurations) {
            assertTrue(configuration.getSelected().contains("Root"));
        }
    }
}
