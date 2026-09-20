# QuickBar Architecture

## Data layer

Room contains three tables:

- `snippets`: user-defined shortcut buttons and text;
- `scripts`: automation definitions;
- `script_steps`: ordered actions belonging to a script.

The accessibility service observes enabled snippets and scripts through Flow, so changes in the editor are reflected in the overlay without restarting the service.

## Accessibility service

`QuickBarAccessibilityService` owns the runtime components used outside the main Activity:

- `OverlayController` creates the top `TYPE_ACCESSIBILITY_OVERLAY` window;
- `InputController` finds editable nodes and performs text/focus/IME actions;
- the service executes script steps sequentially in a coroutine.

The overlay uses `FLAG_NOT_FOCUSABLE` so tapping QuickBar does not intentionally take input focus away from the target editor.

## Text insertion

The primary path is:

1. find the focused editable `AccessibilityNodeInfo`;
2. read its current selection;
3. build a new value with the snippet inserted at the selection;
4. call `ACTION_SET_TEXT`;
5. restore the cursor after the inserted text with `ACTION_SET_SELECTION`.

If `ACTION_SET_TEXT` fails, QuickBar falls back to clipboard + `ACTION_PASTE`.

## Automation

The script engine intentionally uses semantic actions rather than privileged raw input injection. Current step types are:

- `insert_snippet`;
- `insert_text`;
- `next_field`;
- `ime_enter`;
- `delay`.

The engine aborts at the first failed step. This is a deliberate safety property: continuing after a missing target can enter later data into the wrong field.
