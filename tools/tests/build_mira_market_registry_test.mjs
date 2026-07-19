import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import {
  ENTRY_MARKER,
  UPDATE_MARKER,
  buildEntryFromIssue,
  fnv1a32Hex,
  writeMarketSite,
} from "../build_mira_market_registry.mjs";

const request = {
  type: "toolpkg",
  title: "Mira Test Package",
  description: "A test package",
  detail: "Details",
  categoryId: "tools",
  allowPublicUpdates: true,
  version: {
    version: "1.0.0",
    formatVer: "1",
    minAppVer: "0.1.0",
    maxAppVer: "1.99.99",
    projectId: "test-project",
    runtimePackageId: "test.runtime",
  },
  asset: {
    kind: "github_release_asset",
    url: "https://github.com/publisher/MiraForge/releases/download/v1/test.zip",
    assetName: "test.zip",
    sha256: "a".repeat(64),
  },
};

const issue = {
  number: 42,
  state: "open",
  created_at: "2026-07-19T00:00:00Z",
  updated_at: "2026-07-19T01:00:00Z",
  reactions: { "+1": 3 },
  user: { id: 99, login: "publisher", avatar_url: "https://avatars.example/publisher" },
  body: `${ENTRY_MARKER}\n\n\`\`\`json\n${JSON.stringify({ kind: "mira_market_entry", schemaVersion: 1, request })}\n\`\`\``,
};

const updateRequest = {
  version: { ...request.version, version: "1.1.0" },
  asset: { ...request.asset, url: "https://github.com/publisher/MiraForge/releases/download/v1.1/test.zip" },
};
const comments = [
  {
    id: 7,
    created_at: "2026-07-19T02:00:00Z",
    user: issue.user,
    body: `${UPDATE_MARKER}\n\n\`\`\`json\n${JSON.stringify({ kind: "mira_market_update", schemaVersion: 1, entryId: "mira-42", includeEntryPatch: false, request: updateRequest })}\n\`\`\``,
  },
  {
    id: 8,
    created_at: "2026-07-19T03:00:00Z",
    user: { id: 100, login: "attacker" },
    body: `${UPDATE_MARKER}\n\n\`\`\`json\n${JSON.stringify({ kind: "mira_market_update", schemaVersion: 1, entryId: "mira-42", includeEntryPatch: false, request: updateRequest })}\n\`\`\``,
  },
];

const entry = buildEntryFromIssue(issue, comments);
assert.equal(entry.id, "mira-42");
assert.equal(entry.latestVersion.version, "1.1.0");
assert.equal(entry.versions.length, 2);
assert.equal(entry.stateCode, "approved");
assert.equal(entry.stats.likes, 3);
assert.throws(
  () => buildEntryFromIssue({ ...issue, body: issue.body.replace('"type":"toolpkg"', '"type":"../bad"') }, []),
  /Invalid market type/,
);

const root = await fs.mkdtemp(path.join(os.tmpdir(), "mira-market-test-"));
try {
  const entriesDir = path.join(root, "market", "v2", "entries");
  await fs.mkdir(entriesDir, { recursive: true });
  await fs.writeFile(
    path.join(entriesDir, "00.json"),
    JSON.stringify({ ok: true, entriesById: {} }),
    "utf8",
  );
  await writeMarketSite(root, [entry], "2026-07-19T04:00:00Z");

  const shard = fnv1a32Hex(entry.id).slice(0, 2);
  const stored = JSON.parse(await fs.readFile(path.join(entriesDir, `${shard}.json`), "utf8"));
  assert.equal(stored.entriesById[entry.id].title, request.title);

  const list = JSON.parse(
    await fs.readFile(path.join(root, "market", "v2", "lists", "all", "updated", "page-1.json"), "utf8"),
  );
  assert.equal(list.items[0].id, entry.id);

  const publisherShard = fnv1a32Hex(String(issue.user.id)).slice(0, 2);
  const publishers = JSON.parse(
    await fs.readFile(path.join(root, "market", "v2", "private", "publishers", `${publisherShard}.json`), "utf8"),
  );
  assert.equal(publishers.authors[String(issue.user.id)].entries[0].id, entry.id);
} finally {
  await fs.rm(root, { recursive: true, force: true });
}

process.stdout.write("Mira market registry tests passed\n");
