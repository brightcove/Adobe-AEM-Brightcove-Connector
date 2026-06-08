// BCON-174 — "Can't edit a Playlist in modal".
// Two issues, one root cause: the playlist-row click target (the
// `.edit-playlist` button) carried only `data-playlist-id`. The
// `data-playlist-name` and `data-playlist-type` attrs lived on the row
// checkbox, NOT the button — so `editPlaylistHandler` always opened the
// modal with an empty name (no pre-fill) and an unknown type (treated as
// smart, never sending the videos array). Saving sent `playlistName=` to
// the server. After the fix the button carries name + type, so the modal
// pre-fills and Update persists.
const { test, expect, openAdmin } = require('../fixtures');

test('Edit Playlist modal pre-fills name and Update sends it', async ({ page }) => {
  await openAdmin(page);

  // Switch to Playlists tab and wait for rows to render.
  await page.locator('#allPlaylists').click();
  await page.waitForSelector('#tbData tr .edit-playlist', { timeout: 20_000 });

  const $btn = page.locator('#tbData tr .edit-playlist').first();
  const expectedName = (await $btn.innerText()).trim();
  expect(expectedName.length).toBeGreaterThan(0);

  // Regression guard: the click target must carry the data attrs the
  // handler reads. Before the fix `data-playlist-name` lived only on the
  // sibling checkbox.
  await expect($btn).toHaveAttribute('data-playlist-name', expectedName);
  await expect($btn).toHaveAttribute('data-playlist-type', /.+/); // any non-empty value

  // Open the modal and assert the name field is pre-populated with the
  // playlist's current name (was empty before the fix).
  await $btn.click();
  await expect(page.locator('#editPlaylistModal')).toBeVisible();
  await expect(page.locator('#epPlaylistName')).toHaveValue(expectedName);

  // Mock the update_playlist call so the test is deterministic and doesn't
  // rename a live playlist. Capture the outgoing request so we can assert
  // the new name was actually sent.
  const newName = expectedName + ' (e2e-edit-test)';
  let capturedUpdateUrl = null;
  await page.route(/\/bin\/brightcove\/api\.js.*a=update_playlist/, async (route) => {
    capturedUpdateUrl = route.request().url();
    const cb = new URL(capturedUpdateUrl).searchParams.get('callback') || 'cb';
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: `${cb}({"id":"fake","name":"${newName}"});`,
    });
  });

  await page.fill('#epPlaylistName', newName);
  await page.locator('#epUpdate').click();

  await expect.poll(() => capturedUpdateUrl, { timeout: 10_000 }).not.toBeNull();
  const decoded = decodeURIComponent(capturedUpdateUrl);
  // The save must carry the new playlist name — not an empty string.
  expect(decoded).toContain('playlistName=' + newName);
  expect(decoded).not.toMatch(/playlistName=(&|$)/);
});
