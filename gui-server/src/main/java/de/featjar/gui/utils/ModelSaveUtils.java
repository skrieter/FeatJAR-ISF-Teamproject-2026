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
package de.featjar.gui.utils;

import org.eclipse.glsp.server.actions.SaveModelAction;
import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
import org.eclipse.glsp.server.model.GModelState;

/**
 * Writes the current in-memory model back to the EMF file on disk.
 *
 * {@code FeatureModelGuiCommand} reloads the model from that file whenever it
 * receives a save or stop signal, so the file has to be up to date before a signal is sent.
 */
public final class ModelSaveUtils {

    private ModelSaveUtils() {}

    public static void flushModelToDisk(final SourceModelStorage sourceModelStorage, final GModelState modelState) {
        sourceModelStorage.saveSourceModel(new SaveModelAction());
        modelState.saveIsDone();
    }
}