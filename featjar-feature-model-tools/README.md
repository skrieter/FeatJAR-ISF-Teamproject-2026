# FeatJAR Feature Model Tools

## Description

The FeatJAR Feature Model Tools extension provides tools for creating, editing, and analyzing FeatJAR feature models in Visual Studio Code.


## Requirements

- Node.js and npm
- The FeatJAR executable must be stored in the current user's home directory
  at `~/.featjar-bin/feat.jar`. The extension uses this executable for
  satisfiability checks and to start the GUI.
- Java must be installed and available through the `java` command.
  Use the Java version required by your FeatJAR build.


## Features

### Open a feature model in the FeatJAR GUI

Open a `.uvl` file to use the **FeatJAR GUI** editor by default. The extension
starts FeatJAR with the file as input and opens the resulting GUI URL in VS
Code's Simple Browser.

You can also right-click a `.uvl` file in the Explorer and select
**FeatJAR: Open GUI**, or run **FeatJAR: Open GUI** from the Command Palette.

### Check satisfiability

Right-click a `.uvl`, `.xml`, or `.dimacs` file in the Explorer and select
**Check Satisfiability**. The extension reports whether the model is
satisfiable.

### Browse UVL files with a sidebar function 

The FeatJAR sidebar lists the `.uvl` files in the current workspace,
providing quick access to your feature models.

### Count configurations

Calculate the number of valid configurations of a feature model.
This shows how many different combinations of features satisfy
the model's constraints.

### Analyze core features

Identify features that are selected in every valid configuration
of the feature model.

### Analyze dead features

Identify features that cannot be selected in any valid configuration
of the feature model.

### Model statistics

Display statistics about the selected feature model.

### File trust confirmation

When opening a model with **FeatJAR: Open GUI**, the extension
displays a confirmation dialog if the file is not yet trusted,
even when the workspace is trusted.
 
The file trust check can be enabled or disabled in VS Code Settings.
Open Settings with `Ctrl+,` and search for `FeatJAR` to find this option.



## Run the extension

1. Open a terminal in this folder:

   ```bash
   cd featjar-feature-model-tools
   ```

2. Install the dependencies:

   ```bash
   npm install
   ```

3. Compile the extension:

   ```bash
   npm run compile
   ```

4. Open the project in Visual Studio Code and press `F5`.

5. In the new Extension Development Host window, open a `.uvl` file or run a
   FeatJAR command from the Command Palette with `Ctrl+Shift+P`.


## Testing

The extension uses the VS Code Extension Test framework.

Before running the tests, make sure that the current FeatJAR build is
available at:

~/.featjar-bin/feat.jar

Run all tests with:

    npm test

The tests are located in:

    src/test/

Test UVL models are located in:

    resources/

The integration tests start the FeatJAR ExtensionShell and test the
communication between the VS Code extension and FeatJAR.

Currently tested functionality includes:

- satisfiability checking
- unsatisfiability checking
- configuration counting
- core/dead feature analysis

When adding a new FeatJAR command, corresponding tests should be added
to `src/test/extension.test.ts`.


## Troubleshooting

### FeatJAR executable not found

Make sure that the FeatJAR executable is stored at:

`~/.featjar-bin/feat.jar`

The extension may be using an outdated FeatJAR executable.

1. Build the current FeatJAR version from the project's source code.
2. Copy the newly generated `feat.jar` into `~/.featjar-bin/`,
   replacing the existing `feat.jar`.
3. Restart the Extension Development Host and try again.


### Java not found

Check whether Java is available by running:

    java -version

If the command is not found, install Java and make sure it is
available on your system's PATH.

### A FeatJAR command fails

Make sure that the input file exists, uses a supported format,
and contains a valid feature model.