const LEGACY_WORLD_BOOK_DIR = "/sdcard/Download/Operit/worldbook";
const LEGACY_WORLD_BOOK_FILE = `${LEGACY_WORLD_BOOK_DIR}/entries.json`;

export function getWorldBookDir(): string {
  return ToolPkg.getConfigDir();
}

export function getWorldBookFile(): string {
  return `${getWorldBookDir()}/entries.json`;
}

async function deleteLegacyWorldBookStorage(): Promise<void> {
  try {
    const legacyFileExists = await Tools.Files.exists(LEGACY_WORLD_BOOK_FILE);
    if (legacyFileExists?.exists) {
      await Tools.Files.deleteFile(LEGACY_WORLD_BOOK_FILE);
    }
  } catch (_error) {
  }

  try {
    const legacyDirExists = await Tools.Files.exists(LEGACY_WORLD_BOOK_DIR);
    if (legacyDirExists?.exists) {
      await Tools.Files.deleteFile(LEGACY_WORLD_BOOK_DIR, true);
    }
  } catch (_error) {
  }
}

export async function ensureWorldBookStorage(): Promise<void> {
  const worldBookDir = getWorldBookDir();
  const worldBookFile = getWorldBookFile();

  await Tools.Files.mkdir(worldBookDir, true);

  const currentFileExists = await Tools.Files.exists(worldBookFile);
  if (!currentFileExists?.exists) {
    const legacyFileExists = await Tools.Files.exists(LEGACY_WORLD_BOOK_FILE);
    if (legacyFileExists?.exists) {
      const legacyFile = await Tools.Files.read(LEGACY_WORLD_BOOK_FILE);
      const migratedContent = String(legacyFile?.content || "").trim() || "[]";
      await Tools.Files.write(worldBookFile, migratedContent, false);
    } else {
      await Tools.Files.write(worldBookFile, "[]", false);
    }
  }

  await deleteLegacyWorldBookStorage();
}

export async function readWorldBookEntries<T>(): Promise<T[]> {
  await ensureWorldBookStorage();

  try {
    const fileResult = await Tools.Files.read(getWorldBookFile());
    if (!fileResult?.content) {
      return [];
    }

    const parsed = JSON.parse(fileResult.content);
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch (_error) {
    return [];
  }
}

export async function writeWorldBookEntries(entries: unknown[]): Promise<void> {
  await ensureWorldBookStorage();
  await Tools.Files.write(getWorldBookFile(), JSON.stringify(entries, null, 2));
}
