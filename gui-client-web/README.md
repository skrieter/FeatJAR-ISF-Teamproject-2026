# gui-client

This project is a *standalone client* which can be used in combination with the gui-server project to display feature models.

This client is based on Eclipse GLSP

## Usage

Start the editor with the `gui` command:

    java -jar all/build/libs/feat.jar gui --input formula/src/testFixtures/resources/GPL/model.xml --output gpl-edited.xml

This starts a local server and opens the editor in your default browser.

## Features

### Adding features
- Select a feature and press `Insert` to add an optional feature below it. `Ctrl+Alt+M` adds a mandatory feature, `Ctrl+Alt+U` a feature with a cardinality.
- Or right-click a feature and choose **New Feature**.
- New features are placed directly below the selected parent, with no extra click needed.

### Feature types and cardinalities
Right-click a feature to choose **Make Mandatory**, **Make Optional**, **Make Abstract**, **Make Concrete** or **Set Bounds**.

**Set Bounds** asks for a lower and an upper bound. Use `*` (or `-1`) for an unbounded upper bound. Features that can be chosen multiple times show their bounds above the node, e.g. `2..5` or `1..*`. Mandatory and optional features show no label, since their circle marker already shows the type.

### Groups
Right-click a group node to switch it between **OR**, **XOR** and **AND**, or to **Set Bounds** for a cardinality group. All group types are drawn at a consistent size.

### Constraints
- Right-click on empty canvas and choose **Add Constraint**. Right-click a constraint and choose **Delete** to remove it.
- While you edit a constraint, it is checked immediately. An error message appears if the formula is invalid (e.g. a missing operand or an open bracket) or refers to a feature that doesn't exist.
- Constraints use the short symbols: `-` (not), `&` (and), `|` (or), `=>` (implies), `<=>` (equivalent). Example: `Directed => DFS | BFS`

### Colors
Select exactly one feature and click **Set Color...** in the upper left panel. Enter a color name (e.g. `red`) or a hex code (e.g. `#2e7d32`). The color is stored as the feature's `color` attribute, so colors from input files are shown as well.

### Attributes
Tick **Show attributes** in the upper left panel to display feature attributes in the tree. Untick it to hide them again, which keeps large models readable.

### Large models
- **Search:** type in the **Search features...** bar at the top to see matching features in a dropdown. Press `Enter` (or click a suggestion) to select and center a match. Press `Enter` again for the next match, `Shift+Enter` for the previous one.
- **Collapse subtrees:** right-click a feature or group and choose **Collapse Subtree** to hide everything below it. A badge such as `+12` shows how many features are hidden. Choose **Expand Subtree** to show them again. The collapsed state is only kept for the current session and is not saved to the file.

### Keyboard shortcuts

| Shortcut | Action |
|---|---|
| `Insert` | Add an optional feature below the selected feature |
| `Ctrl+Alt+M` | Add a mandatory feature |
| `Ctrl+Alt+U` | Add a feature with a cardinality |
| `Ctrl+Alt+S` | Save |
| `Ctrl+Alt+E` | Save and exit |
| `Enter` / `Shift+Enter` | In the search bar: next / previous match |

## Build

### Using Gradle 
```bash
./gradlew build
```
Or:
```windows Powershell 
cd gui-client-web
npx tsc -b
npx webpack
cd ..
.\gradlew build
cd all
.\gradlew build
Remove-Item -Recurse -Force "$env:USERPROFILE\.featjar-bin\gui" -ErrorAction SilentlyContinue java -jar build\libs\feat.jar gui --input 

### Initial Build
```bash
./gradlew yarnInstall
```

or

```bash
yarn install
```

### Every Build
```bash
./gradlew yarnBuild
```

or

```bash
yarn build
```

## Where features are implemented (client side)

| Feature | Main files in `src/` |
|---|---|
| Add feature without an extra click | `immediate-node-creation-tool.ts` |
| Keyboard shortcuts | `app.ts` |
| Set Color button | `session-management-panel.ts`, `set-type-actions.ts`, `feature-node-view.tsx` |
| Context menu (New Feature, types, groups, constraints) | `node-context-menu.ts` |
| "Show attributes" toggle | `session-management-panel.ts`, `set-type-actions.ts` |
| Cardinality labels, Set Bounds, `*` for unbounded | `feature-node-view.tsx`, `node-context-menu.ts`, `css/diagram.css` |
| Search bar with suggestions | `feature-search-bar.ts`, `feature-search-provider.ts`, `feature-search-utils.ts` |
| Collapse/expand subtrees, `+N` badge | `toggle-collapse-action.ts`, `node-context-menu.ts`, `feature-node-view.tsx` |
