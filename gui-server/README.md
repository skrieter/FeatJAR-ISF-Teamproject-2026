# gui-server

This project contains the backend server for a graphical user interface for [FeatJAR](https://github.com/FeatureIDE/FeatJAR).
The client is located in the project gui-client-web.
## Where features are implemented (server side)

| Feature | Main files in `src/main/java/de/featjar/gui/` |
|---|---|
| Node sizes (e.g. AND groups) | `model/FeatureModelGModelFactory.java` |
| Feature colors from the `color` attribute | `types/AttributeKeys.java`, `utils/AttributeKeysUtils.java`, `operation/handler/SetFeatureColorHandler.java` |
| Create/delete constraints | `operation/handler/CreateConstraintHandler.java`, `operation/handler/DeleteIdentifiableNodeHandler.java` |
| Constraint validation while editing | `utils/FeatureModelLabelEditValidator.java`, `utils/IdentifiableResolver.java`, `operation/handler/LabelEditHandler.java` |
| Show/hide attributes; attributes written to the EMF file | `operation/handler/ToggleShowAttributesHandler.java`, `io/EMFFeatureModelWriter.java` |
| Feature and group cardinalities | `types/CardinalityType.java`, `utils/CardinalityUtils.java`, `operation/handler/SetCardinality*Handler.java`, `operation/handler/create/feature/CreateMultipleFeatureNodeHandler.java` |
| Collapse/expand subtrees | `action/handler/ToggleCollapseHandler.java`, `utils/CollapseUtils.java` |
| Save and Exit | `utils/ModelSaveUtils.java`, `action/handler/SaveHandler.java`, `action/handler/ExitHandler.java` |

## How saving works

The GUI runs as two processes:
- this **server** holds the edited feature model **in memory**,
- the **parent process** (`FeatureModelGuiCommand` in gui-client-web) writes the output file.

They communicate through the server's standard output using these signals (defined in `FeatureModelWebsocketLauncher`):

| Signal | Meaning |
|---|---|
| `server_start` | Server is ready; the parent opens the browser |
| `server_save` | Write the output to `--output`, or print it to the command line |
| `server_save_to:<path>` | Write the output to the path the user entered in the popup |
| `server_stop` | Write the output and shut down |

On a save or stop signal, the parent **reloads the model from `app/gui_model.featuremodel` on disk**. Therefore `SaveHandler` and `ExitHandler` must write the in-memory model to that file **before** sending the signal, using `ModelSaveUtils.flushModelToDisk(...)`. If you add another action that triggers saving, call it first as well; otherwise the output contains the model from startup.

If the GUI was started without `--output` (environment variable `FEATJAR_GUI_OUTPUT_CONFIGURED` is not `true`) and the `SaveAction` has no `outputPath` yet, `SaveHandler` returns a `PromptSaveLocationAction` so the client asks the user for a file path.
