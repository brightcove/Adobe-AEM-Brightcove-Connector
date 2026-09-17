# The `.pml-dialog` confirmation dialog

How the admin tool's confirmation dialog is laid out and shown, and the two ways
it silently breaks.

Read this before touching `.pml-dialog*` CSS or `showPopup()`. If you change what it
describes, update it in the same PR.

## What it is

One markup block in `brightcoveadmin.html` (`.pml-dialog` > `.pml-dialog_container` >
header / content / footer) reused by every confirmation in the admin tool, filled in and
shown by `showPopup(title, message, primaryText, secondaryText, onSuccess, onCancel)` in
`brcUI.js`. Live callers: switch account, bulk-delete playlists, create new label.

## Layout: the parent centers, the container does not position itself

```
.pml-dialog            position:fixed, full viewport, display:none
                       align-items:center; justify-content:center
.pml-dialog_container  NOT positioned; centered by the parent's flexbox
.pml-dialog_content    the scrolling region: overflow-y:auto; max-height:50vh
```

⚠️ **`.pml-dialog_container` must not be positioned.** It used to be
`position:absolute; top:calc(50% - 10vh)`, which pins the dialog's **top** near the
middle of the viewport instead of centering the dialog. A short dialog looks roughly
right, which is why this survived; a tall one runs off the bottom of the screen and takes
its footer buttons with it, so the user cannot confirm or cancel. Ported from the on-prem
line (`afa58e7`, "playlist modal height"), `ONPREM-PARITY-PLAN.md` §3 Phase 3 item 4.

⚠️ **`showPopup()` must set `display:flex`, not call jQuery `.show()`.** `.pml-dialog`
is `display:none` in CSS, and `.show()` restores a `<div>` to `display:block`, which
drops `align-items`/`justify-content` entirely and silently un-centers the dialog. This
is the trap that makes the CSS fix look like it did not work.

⚠️ **The scrolling region is `.pml-dialog_content`, not the container.** Bounding the
container instead would let a long body push the footer outside it. The on-prem fix
bounded `.playlist-listing`/`.label-listing`, which are the dead selectors described
below; on this line the equivalent live region is the content block.

## The dead label/playlist listing dialogs

`suggestLabelsForVideo` and `suggestVideosForPlaylist` in `brcUI.js`, and the two keyup
binders that feed them, are **unreachable**. They append into `.label-listing` /
`.playlist-listing` inside `.pml-dialog`, and that markup exists nowhere: not in
`brightcoveadmin.html`, not anywhere else in `ui.apps`, and not in the served DOM
(measured 2026-09-17 on both a cloud and an on-prem instance). The cloud line replaced
those listings with `#editPlaylistModal` and the label pills.

🔴 Both carry a live bug if they are ever revived: `$item.localName` on a jQuery object is
always `undefined`, so a click landing on the add-icon `<img>` keeps the `<img>` as
`$item` and reads `data-name`/`data-id` off it. The on-prem line fixed exactly that in
`afa58e7`; it is deliberately **not** ported, because a fix to unreachable code cannot be
verified. Either delete the two functions and their binders, or restore the markup and
then port the fix.

## How the layout is proved

`tests/e2e/specs/bcon-pml-dialog-layout.spec.js`, in the pre-QA gate on both platforms,
six tests (three checks at two viewports):

1. **Centered, opened through the real UI** (the filter panel's "+ Create New Label",
   the same path BCON-182 uses): overlay computed `display/align-items/justify-content`,
   container computed `position: static`, and the container's measured center within 2px
   of the viewport center on both axes. Numbers, not screenshots: vision cannot tell a
   dialog centered at 450px from one topped at 390px.
2. **A tall dialog stays inside the viewport and its body actually scrolls**: container
   `top >= 0`, `bottom <= innerHeight`, content `overflow-y: auto`, and
   `scrollHeight > clientHeight`, so a run where the content never overflowed cannot pass
   by accident. This one calls the product's own `showPopup()` with a long body, because
   the only live surface with a long list is the bulk-delete confirmation and driving
   that in an automated spec risks deleting real playlists from a shared account.
3. **Negative control**: the pre-fix CSS is re-applied with `addStyleTag` and checks 1
   and 2 are required to go red. Without it, a root-served, short-content run would pass
   whether the fix were present or not.

Both viewports matter: the defect only appears when the content is tall relative to the
viewport, so a single roomy viewport proves nothing.
