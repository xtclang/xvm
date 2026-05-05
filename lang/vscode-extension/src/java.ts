import * as fs from "node:fs";
import * as https from "node:https";
import * as http from "node:http";
import * as path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import * as vscode from "vscode";
import { findRuntimes } from "jdk-utils";

const REQUIRED_MAJOR = 25;
const ADOPTIUM_API_BASE = `https://api.adoptium.net/v3/assets/latest/${REQUIRED_MAJOR}/hotspot`;

/**
 * Finds a Java 25+ executable.
 *
 * Priority:
 *   1. xtc.java.home VS Code setting
 *   2. jdk-utils discovery (JAVA_HOME, PATH, SDKMAN, mise, asdf, jEnv, Homebrew, Gradle cache, etc.)
 *   3. Previously downloaded JRE in extension global storage
 *   4. Download from Adoptium and cache in global storage
 */
export async function findJavaExecutable(
  context: vscode.ExtensionContext,
): Promise<string> {
  // 1. User-configured path
  const configured = configuredJavaHome();
  if (configured) {
    return configured;
  }

  // 2. jdk-utils: comprehensive cross-platform JDK/JRE discovery
  const runtimes = await findRuntimes({ checkJavac: false, withVersion: true });
  const suitable = runtimes
    .filter((r) => (r.version?.major ?? 0) >= REQUIRED_MAJOR)
    .sort((a, b) => (b.version?.major ?? 0) - (a.version?.major ?? 0));
  if (suitable.length > 0) {
    const javaExe = process.platform === "win32" ? "java.exe" : "java";
    return path.join(suitable[0].homedir, "bin", javaExe);
  }

  // 3. Previously downloaded JRE in global storage
  const storedJava = findJavaBinInDir(jreStorageDir(context));
  if (storedJava) {
    return storedJava;
  }

  // 4. Download from Adoptium, cache in global storage
  return downloadJre(context);
}

function configuredJavaHome(): string | null {
  const configured = vscode.workspace
    .getConfiguration("xtc")
    .get<string>("java.home", "")
    .trim();
  if (!configured) {
    return null;
  }
  const javaExe = process.platform === "win32" ? "java.exe" : "java";
  const candidate = path.join(configured, "bin", javaExe);
  return fs.existsSync(candidate) ? candidate : null;
}

function jreStorageDir(context: vscode.ExtensionContext): string {
  return path.join(context.globalStorageUri.fsPath, "jre");
}

/** Recursively search for `bin/java[.exe]` within a directory tree (max depth 6). */
function findJavaBinInDir(dir: string): string | null {
  const javaExe = process.platform === "win32" ? "java.exe" : "java";

  function search(d: string, depth: number): string | null {
    if (depth > 6) {
      return null;
    }
    let entries: string[];
    try {
      entries = fs.readdirSync(d);
    } catch {
      return null;
    }
    if (entries.includes("bin")) {
      const candidate = path.join(d, "bin", javaExe);
      if (fs.existsSync(candidate)) {
        return candidate;
      }
    }
    for (const entry of entries) {
      const full = path.join(d, entry);
      try {
        if (fs.statSync(full).isDirectory()) {
          const found = search(full, depth + 1);
          if (found) {
            return found;
          }
        }
      } catch {
        /* ignore */
      }
    }
    return null;
  }

  return search(dir, 0);
}

async function downloadJre(context: vscode.ExtensionContext): Promise<string> {
  const osMap: Record<string, string> = {
    darwin: "mac",
    linux: "linux",
    win32: "windows",
  };
  const archMap: Record<string, string> = { x64: "x64", arm64: "aarch64" };
  const adoptiumOs = osMap[process.platform] ?? "linux";
  const adoptiumArch = archMap[process.arch] ?? "x64";

  const apiUrl = `${ADOPTIUM_API_BASE}?architecture=${adoptiumArch}&image_type=jre&jvm_impl=hotspot&os=${adoptiumOs}&vendor=eclipse`;
  const downloadUrl = await fetchAdoptiumDownloadUrl(apiUrl);

  const destDir = jreStorageDir(context);
  fs.mkdirSync(destDir, { recursive: true });

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: `Downloading Java ${REQUIRED_MAJOR} JRE for XTC Language Support`,
      cancellable: false,
    },
    async (progress) => {
      const archiveName = path.basename(new URL(downloadUrl).pathname);
      const archivePath = path.join(destDir, archiveName);

      await downloadFile(downloadUrl, archivePath, (received, total) => {
        if (total > 0) {
          const mb = (n: number) => Math.round(n / 1_048_576);
          progress.report({
            increment: (received / total) * 100,
            message: `${mb(received)} / ${mb(total)} MB`,
          });
        }
      });

      progress.report({ message: "Extracting..." });
      await extractArchive(archivePath, destDir);
      try {
        fs.rmSync(archivePath, { force: true });
      } catch {
        /* ignore */
      }
    },
  );

  const javaExe = findJavaBinInDir(destDir);
  if (!javaExe) {
    throw new Error(
      `Java ${REQUIRED_MAJOR} JRE was downloaded and extracted but the java binary could not be found in ${destDir}`,
    );
  }
  if (process.platform !== "win32") {
    try {
      fs.chmodSync(javaExe, 0o755);
    } catch {
      /* ignore */
    }
  }
  return javaExe;
}

async function fetchAdoptiumDownloadUrl(apiUrl: string): Promise<string> {
  const data = await httpsGetJson(apiUrl);
  const asset = Array.isArray(data) ? data[0] : data;
  const downloadUrl = (asset as { binary?: { package?: { link?: string } } })
    ?.binary?.package?.link;
  if (!downloadUrl) {
    throw new Error(
      `Could not resolve Java ${REQUIRED_MAJOR} JRE download URL from Adoptium API (${apiUrl})`,
    );
  }
  return downloadUrl;
}

function httpsGetJson(url: string): Promise<unknown> {
  return new Promise((resolve, reject) => {
    getWithRedirects(
      url,
      (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode} from ${url}`));
          return;
        }
        const chunks: Buffer[] = [];
        res.on("data", (chunk: Buffer) => chunks.push(chunk));
        res.on("end", () => {
          try {
            resolve(JSON.parse(Buffer.concat(chunks).toString()));
          } catch (e) {
            reject(e);
          }
        });
        res.on("error", reject);
      },
      reject,
    );
  });
}

function downloadFile(
  url: string,
  dest: string,
  onProgress: (received: number, total: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    getWithRedirects(
      url,
      (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(`HTTP ${res.statusCode} downloading ${url}`));
          return;
        }
        const total = Number.parseInt(res.headers["content-length"] ?? "0", 10);
        let received = 0;
        const out = fs.createWriteStream(dest);
        res.on("data", (chunk: Buffer) => {
          received += chunk.length;
          onProgress(received, total);
        });
        res.pipe(out);
        out.on("finish", resolve);
        out.on("error", reject);
        res.on("error", reject);
      },
      reject,
    );
  });
}

function getWithRedirects(
  url: string,
  callback: (res: http.IncomingMessage) => void,
  onError: (err: Error) => void,
  maxRedirects = 10,
): void {
  if (maxRedirects === 0) {
    onError(new Error(`Too many redirects fetching ${url}`));
    return;
  }
  const mod = url.startsWith("https") ? https : http;
  mod
    .get(url, { headers: { "User-Agent": "vscode-xtc-extension" } }, (res) => {
      if (
        res.statusCode &&
        res.statusCode >= 300 &&
        res.statusCode < 400 &&
        res.headers.location
      ) {
        getWithRedirects(
          res.headers.location,
          callback,
          onError,
          maxRedirects - 1,
        );
        return;
      }
      callback(res);
    })
    .on("error", onError);
}

async function extractArchive(
  archivePath: string,
  destDir: string,
): Promise<void> {
  const execFileAsync = promisify(execFile);
  // Both Unix tar and Windows bsdtar (shipped with Windows 10 1803+) handle .zip and .tar.gz
  const flags = archivePath.endsWith(".zip")
    ? ["xf", archivePath, "-C", destDir]
    : ["xzf", archivePath, "-C", destDir];
  await execFileAsync("tar", flags);
}

/**
 * Common JVM arguments for the XTC server processes (LSP and DAP).
 */
export function buildJvmArgs(serverJar: string, logLevel?: string): string[] {
  return [
    "--enable-native-access=ALL-UNNAMED",
    "-Dapple.awt.UIElement=true",
    "-Djava.awt.headless=true",
    `-Dxtc.logLevel=${logLevel ?? "INFO"}`,
    "-jar",
    serverJar,
  ];
}
