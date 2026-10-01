/*
 * Copyright (C) 2026 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-gui-server.
 *
 * gui-server is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * gui-server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with gui-server. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE> for further information.
 */
package de.featjar.gui.operation;

import org.eclipse.glsp.server.operations.Operation;

/**
 * This file is Ai-Assisted
 * Carries a drag and drop in the diagram to the server: which node was dragged,
 * which node it was dropped on or next to, and where it goes relative to that
 * node
 * ({@link Position#before}, {@link Position#after} or {@link Position#inside}).
 * The KIND constant and the field names have to match the client's
 * MoveNodeAction.
 */
public class MoveNodeOperation extends Operation {

    public static final String KIND = "moveNode";

    /** Where the moved node ends up, relative to the target node. */
    public enum Position {

        /** The node becomes a sibling in front of the target. */
        before,

        /** The node becomes a sibling behind the target. */
        after,

        /** The node becomes a child of the target. */
        inside,
    }

    private String elementId;
    private String targetId;
    private Position position;

    public MoveNodeOperation() {
        super(KIND);
    }

    public String getElementId() {
        return elementId;
    }

    public String getTargetId() {
        return targetId;
    }

    public Position getPosition() {
        return position;
    }
}