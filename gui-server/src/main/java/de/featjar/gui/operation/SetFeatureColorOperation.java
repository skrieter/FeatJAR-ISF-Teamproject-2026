package de.featjar.gui.operation;

import org.eclipse.glsp.server.operations.Operation;

public class SetFeatureColorOperation extends Operation {

    public static final String KIND = "setFeatureColor";
    private String elementId;
    private String color;

    public SetFeatureColorOperation() {
        super(KIND);
    }

    public String getElementId() {
        return elementId;
    }

    public String getColor() {
        return color;
    }
}
