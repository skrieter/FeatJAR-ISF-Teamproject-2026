package de.featjar.featureide;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import de.featjar.feature.model.IFeature;
import de.featjar.feature.model.FeatureTree.Group;

public class FeatureModelBuilderTest {
    @Test
    public void testSetGroupFeaturesIsInToCardinality() {
        FeatureModelBuilder builder = new FeatureModelBuilder();
        IFeature root = builder.addRoot("Root");
        IFeature parent = builder.addFeatureBelow("Parent", root);
        IFeature child = builder.addFeatureBelow("Child", parent);

        builder.setGroupFeaturesIsInToCardinality(child, 2, 4);

        Group group = parent.getFeatureTree().get().getChildrenGroup(0).get();
        assertEquals(2, group.getLowerBound());
        assertEquals(4, group.getUpperBound());

}
}