# gui

This project acts as a wrapper for the gui-server to start it as a stand-alone process.

`GuiServer` starts the gui-server as a child process and reads its standard output line by line. The signals are forwarded to the hooks set by the command (`setStartHook`, `setStopHook`, `setSignalHook`); see the gui-server README for the list of signals.

Values can be passed to the server process as environment variables with `putEnvironmentVariable(...)` before calling `run(...)`. This is used for `FEATJAR_GUI_OUTPUT_CONFIGURED`, which tells the server whether an output file was given.

Typing `save` or `stop` in the terminal sends the same signals directly. Note that `save` from the terminal does not write the server's in-memory model to disk first, so use the Save button in the GUI to save edits.
