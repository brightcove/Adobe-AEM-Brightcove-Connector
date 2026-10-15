// Minimal Video Cloud CMS client for specs that need a throwaway video of their
// own. Credentials come from the environment at run time (the same names
// ~/.brightcove/<label>.env exports); nothing identifying lives in this public
// repo. Only ever touches videos this process created itself.
const https = require('https');

const THROWAWAY_PREFIX = 'e2e-throwaway-';

function creds() {
  const { BRIGHTCOVE_ACCOUNT_ID: account, BRIGHTCOVE_CLIENT_ID: id, BRIGHTCOVE_CLIENT_SECRET: secret } = process.env;
  return account && id && secret ? { account, id, secret } : null;
}

function call(method, host, path, { headers = {}, body } = {}) {
  return new Promise((resolve, reject) => {
    const req = https.request({ method, host, path, headers }, (res) => {
      let data = '';
      res.on('data', (c) => { data += c; });
      res.on('end', () => {
        let json = null;
        try { json = data ? JSON.parse(data) : null; } catch (e) { /* non-JSON body */ }
        resolve({ status: res.statusCode, json, text: data });
      });
    });
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

async function token(c) {
  const basic = Buffer.from(`${c.id}:${c.secret}`).toString('base64');
  const res = await call('POST', 'oauth.brightcove.com', '/v4/access_token', {
    headers: { Authorization: `Basic ${basic}`, 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=client_credentials',
  });
  if (res.status !== 200 || !res.json || !res.json.access_token) {
    throw new Error(`OAuth token request failed (HTTP ${res.status})`);
  }
  return res.json.access_token;
}

// Returns { accountId, create(name, fields), get(id), search(q), ingest(id, url), waitActive(id), del(id), ... }
// bound to the env credentials. The extra lookups (tryGet, getByRef, sources, count, folders) and the
// guarded deletes (delIfThrowaway, delFolderIfThrowaway) serve the publish-tier specs.
async function cmsClient() {
  const c = creds();
  if (!c) throw new Error('BRIGHTCOVE_ACCOUNT_ID / BRIGHTCOVE_CLIENT_ID / BRIGHTCOVE_CLIENT_SECRET are not all set');
  const auth = async () => ({ Authorization: `Bearer ${await token(c)}`, 'Content-Type': 'application/json' });
  const base = `/v1/accounts/${c.account}/videos`;
  return {
    accountId: c.account,
    // `fields` merges extra CMS video fields into the create body, e.g. { tags: ['x'] }.
    async create(name, fields = {}) {
      const res = await call('POST', 'cms.api.brightcove.com', base, { headers: await auth(), body: JSON.stringify({ ...fields, name }) });
      if (res.status !== 201 || !res.json || !res.json.id) throw new Error(`create video failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return String(res.json.id);
    },
    // Attach remote poster + thumbnail images (CMS "remote assets"), so the video has images.poster/
    // images.thumbnail like a real one. `url` must be reachable by whoever later fetches it.
    async addRemoteImages(id, url) {
      for (const kind of ['poster', 'thumbnail']) {
        const res = await call('POST', 'cms.api.brightcove.com', `${base}/${id}/assets/${kind}`, { headers: await auth(), body: JSON.stringify({ remote_url: url }) });
        if (res.status !== 201) throw new Error(`add remote ${kind} failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      }
    },
    async get(id) {
      const res = await call('GET', 'cms.api.brightcove.com', `${base}/${id}`, { headers: await auth() });
      if (res.status !== 200) throw new Error(`get video failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json;
    },
    // CMS video search (`q` is the CMS query syntax). Eventually consistent: a video
    // created or tagged seconds ago may be missing, so poll when absence matters.
    async search(q) {
      const res = await call('GET', 'cms.api.brightcove.com', `${base}?q=${encodeURIComponent(q)}&limit=50`, { headers: await auth() });
      if (res.status !== 200 || !Array.isArray(res.json)) throw new Error(`search videos failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json;
    },
    // Every video matching `q`, paged (search() returns one page of 50). Used by the sweep and the
    // leftover count, which must see past the first page. Capped so a bad query cannot loop forever.
    async searchAll(q) {
      const out = [];
      for (let offset = 0; offset < 100 * 100; offset += 100) {
        const res = await call('GET', 'cms.api.brightcove.com', `${base}?q=${encodeURIComponent(q)}&limit=100&offset=${offset}`, { headers: await auth() });
        if (res.status !== 200 || !Array.isArray(res.json)) throw new Error(`search videos failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
        out.push(...res.json);
        if (res.json.length < 100) break;
      }
      return out;
    },
    // Dynamic Ingest of a public media URL, so the video can become state ACTIVE.
    async ingest(id, url) {
      const body = JSON.stringify({ master: { url }, capture_images: true });
      const res = await call('POST', 'ingest.api.brightcove.com', `/v1/accounts/${c.account}/videos/${id}/ingest-requests`,
        { headers: await auth(), body });
      if (res.status !== 200 && res.status !== 201 && res.status !== 202) throw new Error(`ingest failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
    },
    // The video's Dynamic Ingest jobs, each with `state` (processing -> finished / failed),
    // `error_code`, `error_message` and `submitted_at`. ⚠️ `ingest_jobs` (underscore) on the
    // ingest host, unlike the `ingest-requests` submit path.
    async ingestJobs(id) {
      const res = await call('GET', 'ingest.api.brightcove.com', `/v1/accounts/${c.account}/videos/${id}/ingest_jobs`, { headers: await auth() });
      if (res.status !== 200 || !Array.isArray(res.json)) throw new Error(`list ingest jobs failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json;
    },
    // Resolves true once the video reports state ACTIVE, false on timeout.
    async waitActive(id, timeoutMs = 300_000) {
      const end = Date.now() + timeoutMs;
      while (Date.now() < end) {
        if ((await this.get(id)).state === 'ACTIVE') return true;
        await new Promise((r) => setTimeout(r, 5_000));
      }
      return false;
    },
    async del(id) {
      const res = await call('DELETE', 'cms.api.brightcove.com', `${base}/${id}`, { headers: await auth() });
      if (res.status !== 204 && res.status !== 200 && res.status !== 404) throw new Error(`delete video failed (HTTP ${res.status})`);
    },
    // The video, or null on 404 (deleted / never existed). Other statuses throw.
    // `id` may be `ref:<reference_id>`; ids and uuids need no escaping.
    async tryGet(id) {
      const res = await call('GET', 'cms.api.brightcove.com', `${base}/${id}`, { headers: await auth() });
      if (res.status === 404) return null;
      if (res.status !== 200) throw new Error(`get video failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json;
    },
    // Lookup by reference_id (the connector defaults it to the DAM asset's jcr:uuid,
    // BGS-1705). A direct GET, not a search, so it is not subject to index lag.
    async getByRef(ref) {
      return this.tryGet(`ref:${ref}`);
    },
    async sources(id) {
      const res = await call('GET', 'cms.api.brightcove.com', `${base}/${id}/sources`, { headers: await auth() });
      if (res.status !== 200) throw new Error(`get sources failed (HTTP ${res.status})`);
      return res.json || [];
    },
    // Search-index count. ⚠️ Eventually consistent: a just-created video can be absent.
    async count(q) {
      const res = await call('GET', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/counts/videos?q=${encodeURIComponent(q)}`, { headers: await auth() });
      if (res.status !== 200) throw new Error(`count failed (HTTP ${res.status})`);
      return res.json.count;
    },
    async folders() {
      const res = await call('GET', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/folders`, { headers: await auth() });
      if (res.status !== 200) throw new Error(`list folders failed (HTTP ${res.status})`);
      return res.json || [];
    },
    // Deletes only throwaway folders (name prefix), so a cleanup bug cannot remove a
    // real one. Deleting a folder does not delete the videos in it.
    async delFolderIfThrowaway(folder) {
      if (!folder || !folder.id || !String(folder.name || '').startsWith('e2e-throwaway-')) return false;
      const res = await call('DELETE', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/folders/${folder.id}`, { headers: await auth() });
      if (res.status !== 204 && res.status !== 200 && res.status !== 404) throw new Error(`delete folder failed (HTTP ${res.status})`);
      return true;
    },
    // ---- Folders / playlists / variants (live mutation specs) ----------------------
    // Folder created through the CMS. `name` must carry the throwaway prefix so the guarded
    // delete (delFolderIfThrowaway) and the sweep can remove it. Returns the folder object.
    async createFolder(name) {
      if (!name.startsWith(THROWAWAY_PREFIX)) throw new Error(`refusing to create non-throwaway folder ${name}`);
      const res = await call('POST', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/folders`, { headers: await auth(), body: JSON.stringify({ name }) });
      if (res.status !== 201 || !res.json || !res.json.id) throw new Error(`create folder failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json;
    },
    // Ids of the videos in a folder (a direct read, not the search index).
    async folderVideoIds(folderId) {
      const res = await call('GET', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/folders/${folderId}/videos?limit=100`, { headers: await auth() });
      if (res.status !== 200 || !Array.isArray(res.json)) throw new Error(`list folder videos failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json.map((v) => String(v.id));
    },
    // Explicit playlist of the given video ids. Returns the playlist object.
    async createPlaylist(name, videoIds = []) {
      if (!name.startsWith(THROWAWAY_PREFIX)) throw new Error(`refusing to create non-throwaway playlist ${name}`);
      const body = JSON.stringify({ name, type: 'EXPLICIT', video_ids: videoIds });
      const res = await call('POST', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/playlists`, { headers: await auth(), body });
      if (res.status !== 201 || !res.json || !res.json.id) throw new Error(`create playlist failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json;
    },
    // The playlist, or null on 404. A direct GET, so no index lag.
    async tryGetPlaylist(id) {
      const res = await call('GET', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/playlists/${id}`, { headers: await auth() });
      if (res.status === 404) return null;
      if (res.status !== 200) throw new Error(`get playlist failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json;
    },
    // Every playlist in the account (the list endpoint, paged; not a search, so no index lag).
    async allPlaylists() {
      const out = [];
      for (let offset = 0; ; offset += 100) {
        const res = await call('GET', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/playlists?limit=100&offset=${offset}&sort=name`, { headers: await auth() });
        if (res.status !== 200 || !Array.isArray(res.json)) throw new Error(`list playlists failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
        out.push(...res.json);
        if (res.json.length < 100) return out;
      }
    },
    // Playlist lookup by exact name. null when absent.
    async findPlaylistByName(name) {
      return (await this.allPlaylists()).find((p) => p.name === name) || null;
    },
    async delPlaylistIfThrowaway(id) {
      if (!id) return false;
      const p = await this.tryGetPlaylist(id);
      if (!p || !String(p.name || '').startsWith(THROWAWAY_PREFIX)) return false;
      const res = await call('DELETE', 'cms.api.brightcove.com', `/v1/accounts/${c.account}/playlists/${id}`, { headers: await auth() });
      if (res.status !== 204 && res.status !== 200 && res.status !== 404) throw new Error(`delete playlist failed (HTTP ${res.status})`);
      return true;
    },
    // The video's language variants (array; empty when none).
    async variants(id) {
      const res = await call('GET', 'cms.api.brightcove.com', `${base}/${id}/variants`, { headers: await auth() });
      if (res.status !== 200 || !Array.isArray(res.json)) throw new Error(`list variants failed (HTTP ${res.status}): ${res.text.slice(0, 200)}`);
      return res.json;
    },
    // Removes throwaway videos, playlists and folders older than `maxAgeMs` (default one day),
    // so a run killed before its afterAll does not accumulate fixtures. Prefix-guarded like
    // every other delete here. Videos come from the search index, so one created seconds ago
    // may be missed; a later sweep catches it. Returns the counts removed.
    async sweepThrowaways(maxAgeMs = 24 * 3600 * 1000) {
      const old = (o) => Date.now() - Date.parse(o.created_at) > maxAgeMs;
      const out = { videos: 0, playlists: 0, folders: 0 };
      for (const v of await this.searchAll(THROWAWAY_PREFIX.replace(/-$/, ''))) {
        if (String(v.name).startsWith(THROWAWAY_PREFIX) && old(v) && await this.delIfThrowaway(v.id)) out.videos++;
      }
      for (const p of await this.allPlaylists()) {
        if (String(p.name).startsWith(THROWAWAY_PREFIX) && old(p) && await this.delPlaylistIfThrowaway(p.id)) out.playlists++;
      }
      for (const f of await this.folders()) {
        if (String(f.name).startsWith(THROWAWAY_PREFIX) && old(f) && await this.delFolderIfThrowaway(f)) out.folders++;
      }
      return out;
    },
    // Throwaway objects still in the account: the post-run "left nothing behind" check. Video
    // search hits are re-read directly, because the index keeps listing a deleted video for a
    // long while (measured: days); only a hit that still resolves counts.
    // `names` (optional array of exact names) narrows the count to what one run created.
    async countThrowaways(names) {
      const mine = (o) => String(o.name).startsWith(THROWAWAY_PREFIX) && (!names || names.includes(o.name));
      let videos = 0;
      for (const v of await this.searchAll(THROWAWAY_PREFIX.replace(/-$/, ''))) {
        if (mine(v) && await this.tryGet(v.id)) videos++;
      }
      return {
        videos,
        playlists: (await this.allPlaylists()).filter(mine).length,
        folders: (await this.folders()).filter(mine).length,
      };
    },
    // Re-reads the video and deletes it only when its CURRENT name carries the
    // throwaway prefix, so an id that leaked from the wrong asset cannot remove a real
    // video. Returns true when it deleted something.
    async delIfThrowaway(id) {
      if (!id) return false;
      const v = await this.tryGet(id);
      if (!v) return false;
      if (!String(v.name || '').startsWith('e2e-throwaway-')) {
        console.warn(`[cms] NOT deleting video ${id}: its name does not carry the throwaway prefix`);
        return false;
      }
      await this.del(id);
      return true;
    },
  };
}

module.exports = { creds, cmsClient };
