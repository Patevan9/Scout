# Scout Startup Experience (Parked UX Improvements)

> Status: Parked — design notes for a future startup-flow overhaul. Not yet implemented.

## Core Philosophy

The user should never encounter a "half-awake" Scout. Scout is either preparing or fully present. The startup experience should feel intentional, calm, and consistent from beginning to end.

## 1. Explain what's happening

Instead of only showing humorous rotating messages, add a permanent explanation beneath the progress bar, for example:

> Downloading Scout's offline AI brain...
> This is a one-time setup and may take several minutes.

This reassures users while preserving Scout's personality.

## 2. Unified startup gate

Treat downloading and model initialization as one continuous startup sequence.

Instead of:
- Download finishes
- Scout appears
- Scout says, "My offline brain is still getting ready..."

Use:
- Download model (if needed)
- Load offline brain into memory
- Initialize Scout completely
- Only then allow Scout to appear

The user never interacts with a partially initialized Scout.

## 3. Loading on every launch

Even after the model is already installed, TinyLlama still has to be loaded into memory each time Scout starts.

Instead of immediately showing Scout, display a brief loading screen such as:

> Loading offline brain...

or

> Preparing Scout...

Once initialization is complete, transition into Scout.

## 4. First-online vs. re-online greetings

Use a persistent lifetime flag representing:

> "Has the user already experienced Scout's first startup?"

**First successful startup ever:**
> "Hi... thanks for waiting. My offline brain is ready now."

Set the flag immediately afterward.

**Later model repair, replacement, or upgrade:**
> "Thanks for waiting. My offline brain is ready again."

**Normal daily launches:**
No spoken greeting. Scout simply appears, opens his eyes, and is ready to talk.

## 5. Identity is separate from the AI model

This is an architectural principle, not just a UI decision.

The downloaded AI model is Scout's reasoning engine — not Scout's identity.

Scout's relationship with the user lives in persistent memory:
- Truth DB
- Habit Store
- Journal DB

Replacing or upgrading the offline model should improve how Scout thinks, never erase who Scout has become.

## 6. Landscape from the very first frame

The entire startup sequence should remain in landscape:
- Download screen
- Loading offline brain
- Preparing Scout
- Scout himself

The user rotates the device once and never experiences a jarring orientation change.

## 7. Feature tips in the rotation

Instead of only showing humorous loading messages, rotate useful Scout tips beneath the progress bar (similar to how many games show hints while loading).

These should only describe features that already exist. For example:

> 💡 To enter Scout Settings, simply swipe to the right.
> 💡 Scout remembers what you teach him locally.
> 💡 Your privacy stays on your device whenever possible.
> 💡 You can ask Scout about today's weather when connected to the internet.
> 💡 Scout gets to know you over time.

Interruption is intentionally left out of the rotation until that feature works reliably — every tip should be accurate rather than advertise something still in development. Once interruption is working well, it can simply be added to the rotation.

## Overall Goal

Instead of feeling like:

> Open app → download file → wait → rotate phone → app loads → AI still warming up...

the experience becomes:

> Open Scout → enter Scout's world → Scout prepares quietly → Scout is fully awake → conversation begins.

The startup process becomes part of Scout's personality rather than simply a technical necessity.
