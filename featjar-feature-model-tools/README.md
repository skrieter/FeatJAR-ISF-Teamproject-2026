# FeatJAR Feature Model Tools

## Description

The FeatJAR Feature Model Tools extension provides tools for creating, editing, and analyzing FeatJAR feature models in Visual Studio Code.

## Requirements

- Node.js and npm
- The FeatJAR executable must be stored in the current user's home directory
  at `~/.featjar-bin/feat.jar`. The extension uses this executable for
  satisfiability checks and to start the GUI.

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
