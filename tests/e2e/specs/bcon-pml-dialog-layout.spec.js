// Confirmation-dialog (.pml-dialog) layout — ONPREM-PARITY-PLAN.md §3 Phase 3
// item 4, ported from on-prem afa58e7 ("playlist modal height").
//
// Pre-fix: .pml-dialog_container was position:absolute with top:calc(50% - 10vh),
// which pins the dialog's TOP near the middle of the viewport rather than
// centering the dialog, so a tall dialog ran off the bottom of the screen and
// its footer buttons became unreachable. Nothing bounded the scrolling region
// either. The fix centers the container with flexbox on the parent and makes
// .pml-dialog_content the scrolling region; showPopup() therefore has to show
// the overlay as display:flex, since jQuery's .show() would set display:block
// and silently drop the centering.
//
// Geometry is asserted as NUMBERS (getBoundingClientRect + computed styles):
// a screenshot cannot tell a dialog centered at 450px from one topped at 390px.
// Every check runs at a roomy and a short viewport, because the defect only
// shows when the content is tall relative to the viewport.
const { test, expect, openAdmin } = require('../fixtures');

const VIEWPORTS = [
  { name: 'roomy', width: 1440, height: 900 },
  { name: 'short', width: 1100, height: 620 },
];

// The CSS this replaced, reapplied to make the checks below go red. Keeping it
// here is what proves the assertions can still see the defect.
const PRE_FIX_CSS = `
  .pml-dialog { align-items: flex-start; justify-content: flex-start; }
  .pml-dialog_container { position: absolute; top: calc(50% - 10vh); left: calc(50% - 18vw); }
  .pml-dialog_content { overflow-y: visible; max-height: none; }
`;

// Open the real dialog through the real UI: the filter panel's
// "+ Create New Label" option calls showPopup(). Same path as BCON-182.
async function openDialogViaUI(page) {
  await page.locator('#filterToggle').click();
  await expect(page.locator('#filterPanel')).toBeVisible();
  await page.waitForSelector('#label_list option[value="create"]', { state: 'attached', timeout: 15_000 });
  await page.selectOption('#label_list', 'create');
  await expect(page.locator('.pml-dialog .input-label-name')).toBeVisible();
}

// A TALL dialog. The only live surface with a long list is the bulk-delete
// confirmation, and driving that in an automated spec risks deleting real
// playlists from a shared account, so this calls the product's own showPopup
// with a long body instead. Nothing is submitted: no onSuccess is wired.
async function openTallDialog(page) {
  await page.evaluate(() => {
    const items = Array.from({ length: 40 }, (_, i) => `<li>Item number ${i + 1}</li>`).join('');
    // eslint-disable-next-line no-undef
    showPopup('Tall dialog', `<p>Long body:</p><ul>${items}</ul>`, 'Confirm', 'Cancel',
      function () {}, function () {});
  });
  await expect(page.locator('.pml-dialog_container')).toBeVisible();
}

async function measure(page) {
  return page.evaluate(() => {
    const overlay = document.querySelector('.pml-dialog');
    const container = document.querySelector('.pml-dialog_container');
    const content = document.querySelector('.pml-dialog_content');
    const oc = getComputedStyle(overlay);
    const cc = getComputedStyle(container);
    const ct = getComputedStyle(content);
    const r = container.getBoundingClientRect();
    return {
      overlay: { display: oc.display, alignItems: oc.alignItems, justifyContent: oc.justifyContent },
      container: {
        position: cc.position,
        top: Math.round(r.top), bottom: Math.round(r.bottom),
        centerX: Math.round(r.left + r.width / 2), centerY: Math.round(r.top + r.height / 2),
        width: Math.round(r.width), height: Math.round(r.height),
      },
      content: {
        overflowY: ct.overflowY,
        clientHeight: content.clientHeight,
        scrollHeight: content.scrollHeight,
      },
      viewport: { width: window.innerWidth, height: window.innerHeight },
    };
  });
}

for (const vp of VIEWPORTS) {
  test(`dialog opened from the UI is centered at ${vp.name} ${vp.width}x${vp.height}`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height });
    await openAdmin(page);
    await openDialogViaUI(page);

    const m = await measure(page);
    expect(m.overlay.display).toBe('flex');
    expect(m.overlay.alignItems).toBe('center');
    expect(m.overlay.justifyContent).toBe('center');
    // Not positioned itself: the flex parent centers it.
    expect(m.container.position).toBe('static');
    expect(Math.abs(m.container.centerX - m.viewport.width / 2)).toBeLessThanOrEqual(2);
    expect(Math.abs(m.container.centerY - m.viewport.height / 2)).toBeLessThanOrEqual(2);
  });

  test(`a tall dialog stays inside the viewport and scrolls its body at ${vp.name} ${vp.width}x${vp.height}`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height });
    await openAdmin(page);
    await openTallDialog(page);

    const m = await measure(page);
    expect(m.container.top, 'dialog top is above the viewport').toBeGreaterThanOrEqual(0);
    expect(m.container.bottom, 'dialog bottom runs past the viewport, so its footer buttons are unreachable')
      .toBeLessThanOrEqual(m.viewport.height);
    // The body is the scrolling region, and it is actually scrolling: a
    // max-height that never engages would pass an overflow-only assertion.
    expect(m.content.overflowY).toBe('auto');
    expect(m.content.scrollHeight,
      'content is not actually overflowing, so this run did not exercise the fix')
      .toBeGreaterThan(m.content.clientHeight);
  });

  test(`negative control: the pre-fix CSS breaks both checks at ${vp.name} ${vp.width}x${vp.height}`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height });
    await openAdmin(page);
    await page.addStyleTag({ content: PRE_FIX_CSS });
    await openTallDialog(page);

    const m = await measure(page);
    // Exactly the two things the fix changed, in their pre-fix state.
    const offCenter = Math.abs(m.container.centerY - m.viewport.height / 2) > 2;
    const overflowsViewport = m.container.bottom > m.viewport.height;
    expect(offCenter || overflowsViewport,
      'the pre-fix CSS produced a centered, fully-visible dialog: these assertions ' +
      'can no longer detect the defect they exist to catch').toBe(true);
  });
}
