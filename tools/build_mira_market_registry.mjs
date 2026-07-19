import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

export const ENTRY_MARKER = "<!-- mira-market-v1 -->";
export const UPDATE_MARKER = ENTRY_MARKER;

const PAGE_SIZE = 50;
const SORTS = ["updated", "downloads", "likes"];
const SAFE_SEGMENT = /^[A-Za-z0-9._-]{1,64}$/;

function requireSafeSegment(value, label) {
  if (!SAFE_SEGMENT.test(value)) throw new Error(`Invalid ${label}`);
  return value;
}

function validatePublishRequest(issue, request) {
  requireSafeSegment(request.type, "market type");
  if (request.categoryId) requireSafeSegment(request.categoryId, "market category");
  if (!request.title || request.title.length > 120) throw new Error("Invalid market title");
  if (!request.description || request.description.length > 500) throw new Error("Invalid market description");
  if (!request.version?.version || !request.version?.formatVer || !request.version?.minAppVer) {
    throw new Error("Incomplete market version");
  }
  if (request.asset) {
    const assetUrl = new URL(request.asset.url);
    const owner = issue.user.login.toLowerCase();
    const segments = assetUrl.pathname.split("/").filter(Boolean);
    if (
      assetUrl.protocol !== "https:" ||
      assetUrl.hostname !== "github.com" ||
      segments[0]?.toLowerCase() !== owner ||
      segments[1]?.toLowerCase() !== "miraforge" ||
      segments[2] !== "releases" ||
      segments[3] !== "download"
    ) {
      throw new Error("Market asset must be an author-owned MiraForge Release URL");
    }
    if (!/^[a-fA-F0-9]{64}$/.test(request.asset.sha256)) throw new Error("Invalid market asset SHA-256");
    if (request.asset.ghOwner && request.asset.ghOwner.toLowerCase() !== owner) {
      throw new Error("Market asset owner does not match the Issue author");
    }
    if (request.asset.ghRepo && request.asset.ghRepo.toLowerCase() !== "miraforge") {
      throw new Error("Market asset repository must be MiraForge");
    }
  }
}

export function parseProtocolDocument(body, marker) {
  const normalized = body.trimStart();
  if (!normalized.startsWith(marker)) return null;
  const match = normalized.slice(marker.length).match(/```json\s*([\s\S]*?)```/i);
  if (!match) throw new Error(`Missing JSON block after ${marker}`);
  return JSON.parse(match[1]);
}

export function fnv1a32Hex(value) {
  let hash = 0x811c9dc5;
  for (const char of value) {
    hash ^= char.codePointAt(0);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, "0");
}

function entryShard(value) {
  return fnv1a32Hex(value).slice(0, 2);
}

function asAuthor(user) {
  return {
    id: String(user.id),
    githubId: user.id,
    login: user.login,
    avatar: user.avatar_url,
    avatarUrl: user.avatar_url,
    status: "active",
  };
}

function versionFromRequest(issueNumber, request, sequence, publisherId, publishedAt) {
  const source = request.version;
  return {
    id: `mira-${issueNumber}-v${sequence}`,
    version: source.version,
    formatVer: source.formatVer,
    publisherId,
    minAppVer: source.minAppVer,
    maxAppVer: source.maxAppVer,
    changelog: source.changelog ?? "",
    installConfig: request.repoVersion?.installConfig ?? "{}",
    stateCode: "approved",
    publishedAt,
    projectId: source.projectId ?? "",
    runtimePackageId: source.runtimePackageId ?? "",
  };
}

function assetFromRequest(issueNumber, request, versionId) {
  if (!request.asset) return null;
  return {
    id: `mira-${issueNumber}-asset-${versionId}`,
    versionId,
    kind: request.asset.kind,
    url: request.asset.url,
    sha256: request.asset.sha256,
    name: request.asset.assetName,
    assetName: request.asset.assetName,
  };
}

export function buildEntryFromIssue(issue, comments = []) {
  const document = parseProtocolDocument(issue.body ?? "", ENTRY_MARKER);
  if (!document) return null;
  if (document.kind !== "mira_market_entry" || document.schemaVersion !== 1) {
    throw new Error(`Unsupported Mira market document in issue ${issue.number}`);
  }

  const request = document.request;
  validatePublishRequest(issue, request);
  const author = asAuthor(issue.user);
  const firstVersion = versionFromRequest(issue.number, request, 1, author.id, issue.created_at);
  const firstAsset = assetFromRequest(issue.number, request, firstVersion.id);
  const versions = [firstVersion];
  const assets = firstAsset ? [firstAsset] : [];
  let title = request.title;
  let description = request.description;
  let detail = request.detail ?? "";
  let categoryId = request.categoryId ?? "";
  let allowPublicUpdates = request.allowPublicUpdates !== false;
  let stateCode = issue.state === "open" ? "approved" : "withdrawn";

  const authoredUpdates = comments
    .filter((comment) => comment.user?.login === issue.user.login)
    .map((comment) => ({
      comment,
      document: parseProtocolDocument(comment.body ?? "", UPDATE_MARKER),
    }))
    .filter(({ document: update }) => update !== null)
    .sort((left, right) => left.comment.id - right.comment.id);

  for (const { comment, document: update } of authoredUpdates) {
    if (update.schemaVersion !== 1) {
      throw new Error(`Unsupported Mira market update in comment ${comment.id}`);
    }
    if (update.kind === "mira_market_update") {
      if (update.entryId !== `mira-${issue.number}`) {
        throw new Error(`Mira market update targets another entry in comment ${comment.id}`);
      }
      const updateRequest = update.request;
      validatePublishRequest(issue, {
        type: request.type,
        title: update.includeEntryPatch ? updateRequest.title : title,
        description: update.includeEntryPatch ? updateRequest.description : description,
        categoryId: update.includeEntryPatch ? updateRequest.categoryId : categoryId,
        version: updateRequest.version,
        asset: updateRequest.asset,
      });
      if (update.includeEntryPatch) {
        title = updateRequest.title;
        description = updateRequest.description;
        detail = updateRequest.detail ?? "";
        categoryId = updateRequest.categoryId ?? "";
        allowPublicUpdates = updateRequest.allowPublicUpdates !== false;
      }
      const version = versionFromRequest(
        issue.number,
        updateRequest,
        versions.length + 1,
        author.id,
        comment.created_at,
      );
      versions.push(version);
      const asset = assetFromRequest(issue.number, updateRequest, version.id);
      if (asset) assets.push(asset);
    }
  }

  const latestVersion = versions.at(-1);
  const likes = issue.reactions?.["+1"] ?? 0;
  return {
    type: request.type,
    id: `mira-${issue.number}`,
    title,
    description,
    detail,
    authorId: author.id,
    publisherId: author.id,
    allowPublicUpdates,
    categoryId,
    stateCode,
    createdAt: issue.created_at,
    updatedAt: issue.updated_at,
    publishedAt: issue.created_at,
    source: request.source ?? null,
    artifact: latestVersion.projectId
      ? { projectId: latestVersion.projectId, runtimePkg: latestVersion.runtimePackageId }
      : null,
    assets,
    versions,
    latestVersion,
    reactions: [{ reaction: "+1", content: "+1", total: likes }],
    downloads: 0,
    downloadCount: 0,
    stats: { downloads: 0, likes, lastDownloadAt: null, updatedAt: issue.updated_at },
    author,
    publisher: author,
    contributors: [author],
    featured: false,
  };
}

async function readJson(filePath) {
  return JSON.parse(await fs.readFile(filePath, "utf8"));
}

async function writeJson(filePath, value) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

async function readExistingEntries(siteRoot) {
  const directory = path.join(siteRoot, "market", "v2", "entries");
  const entries = new Map();
  for (const name of await fs.readdir(directory)) {
    if (!name.endsWith(".json")) continue;
    const shard = await readJson(path.join(directory, name));
    for (const [id, entry] of Object.entries(shard.entriesById ?? {})) entries.set(id, entry);
  }
  return entries;
}

function likesOf(entry) {
  return entry.stats?.likes ?? entry.reactions?.find((item) => item.reaction === "+1")?.total ?? 0;
}

function downloadsOf(entry) {
  return entry.stats?.downloads ?? entry.downloadCount ?? entry.downloads ?? 0;
}

function compareEntries(sort) {
  if (sort === "downloads") return (a, b) => downloadsOf(b) - downloadsOf(a);
  if (sort === "likes") return (a, b) => likesOf(b) - likesOf(a);
  return (a, b) => Date.parse(b.updatedAt ?? b.publishedAt ?? 0) - Date.parse(a.updatedAt ?? a.publishedAt ?? 0);
}

async function clearPageFiles(directory) {
  await fs.mkdir(directory, { recursive: true });
  for (const name of await fs.readdir(directory)) {
    if (/^page-\d+\.json$/.test(name)) await fs.unlink(path.join(directory, name));
  }
}

async function writePagedList(directory, entries, sort, generatedAt) {
  await clearPageFiles(directory);
  const sorted = [...entries].sort(compareEntries(sort));
  const pageCount = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  for (let page = 1; page <= pageCount; page += 1) {
    await writeJson(path.join(directory, `page-${page}.json`), {
      ok: true,
      marketVersion: 2,
      generatedAt,
      sort,
      page,
      pageSize: PAGE_SIZE,
      total: sorted.length,
      items: sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
    });
  }
}

async function writeLists(siteRoot, entries, generatedAt) {
  const base = path.join(siteRoot, "market", "v2", "lists");
  const publicEntries = entries.filter((entry) => entry.stateCode === "approved");
  const types = new Set(publicEntries.map((entry) => entry.type).filter(Boolean));
  const categories = new Set(publicEntries.map((entry) => entry.categoryId).filter(Boolean));

  for (const sort of SORTS) {
    await writePagedList(path.join(base, "all", sort), publicEntries, sort, generatedAt);
    for (const type of types) {
      const typed = publicEntries.filter((entry) => entry.type === type);
      await writePagedList(path.join(base, "type", type, sort), typed, sort, generatedAt);
    }
    for (const category of categories) {
      const categorized = publicEntries.filter((entry) => entry.categoryId === category);
      await writePagedList(path.join(base, "category", category, sort), categorized, sort, generatedAt);
      for (const type of types) {
        const typed = categorized.filter((entry) => entry.type === type);
        await writePagedList(path.join(base, "type", type, "category", category, sort), typed, sort, generatedAt);
      }
    }
  }
}

async function writeEntryShards(siteRoot, entries, generatedAt) {
  const grouped = new Map();
  for (const entry of entries) {
    const shard = entryShard(entry.id);
    if (!grouped.has(shard)) grouped.set(shard, {});
    grouped.get(shard)[entry.id] = entry;
  }
  for (const [shard, entriesById] of grouped) {
    await writeJson(path.join(siteRoot, "market", "v2", "entries", `${shard}.json`), {
      ok: true,
      marketVersion: 2,
      generatedAt,
      shard,
      entriesById,
    });
  }
}

async function writePublisherShards(siteRoot, entries, generatedAt) {
  const grouped = new Map();
  for (const entry of entries) {
    const authorId = entry.authorId || entry.publisherId;
    if (!authorId) continue;
    const shard = entryShard(authorId);
    if (!grouped.has(shard)) grouped.set(shard, {});
    const authors = grouped.get(shard);
    if (!authors[authorId]) authors[authorId] = { ok: true, generatedAt, shard, entries: [] };
    authors[authorId].entries.push({
      id: entry.id,
      title: entry.title,
      type: entry.type,
      relation: "owner",
      stateCode: entry.stateCode,
      categoryId: entry.categoryId,
      updatedAt: entry.updatedAt ?? entry.publishedAt ?? "",
      reasonCodes: [],
    });
  }
  for (const [shard, authors] of grouped) {
    for (const author of Object.values(authors)) {
      author.entries.sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt));
    }
    await writeJson(path.join(siteRoot, "market", "v2", "private", "publishers", `${shard}.json`), {
      ok: true,
      marketVersion: 2,
      generatedAt,
      shard,
      authors,
    });
  }
}

export async function writeMarketSite(siteRoot, miraEntries, generatedAt = new Date().toISOString()) {
  const entries = await readExistingEntries(siteRoot);
  for (const entry of miraEntries) entries.set(entry.id, entry);
  const values = [...entries.values()];
  await writeEntryShards(siteRoot, values, generatedAt);
  await writePublisherShards(siteRoot, values, generatedAt);
  await writeLists(siteRoot, values, generatedAt);
}

async function githubPages(url, token) {
  const items = [];
  let next = url;
  while (next) {
    const response = await fetch(next, {
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${token}`,
        "User-Agent": "Mira-Market-Indexer",
        "X-GitHub-Api-Version": "2022-11-28",
      },
    });
    if (!response.ok) throw new Error(`GitHub API ${response.status}: ${await response.text()}`);
    items.push(...await response.json());
    next = response.headers.get("link")?.match(/<([^>]+)>; rel="next"/)?.[1] ?? "";
  }
  return items;
}

async function loadMiraEntries(repository, token) {
  const issues = await githubPages(`https://api.github.com/repos/${repository}/issues?state=all&per_page=100`, token);
  const entries = [];
  for (const issue of issues) {
    if (issue.pull_request || !(issue.body ?? "").includes(ENTRY_MARKER)) continue;
    try {
      const comments = issue.comments > 0
        ? await githubPages(`https://api.github.com/repos/${repository}/issues/${issue.number}/comments?per_page=100`, token)
        : [];
      const entry = buildEntryFromIssue(issue, comments);
      if (entry) entries.push(entry);
    } catch (error) {
      process.stderr.write(`Skipped invalid Mira market issue #${issue.number}: ${error.message}\n`);
    }
  }
  return entries;
}

async function main() {
  const siteIndex = process.argv.indexOf("--site");
  const repoIndex = process.argv.indexOf("--repo");
  if (siteIndex < 0 || repoIndex < 0) throw new Error("Usage: --site <path> --repo <owner/name>");
  const token = process.env.GITHUB_TOKEN;
  if (!token) throw new Error("GITHUB_TOKEN is required");
  const entries = await loadMiraEntries(process.argv[repoIndex + 1], token);
  await writeMarketSite(process.argv[siteIndex + 1], entries);
  process.stdout.write(`Indexed ${entries.length} Mira market issue(s)\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}
