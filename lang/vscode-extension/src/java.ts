import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { execSync } from 'node:child_process';

/**
 * Java discovery - finds a suitable Java 25+ executable.
 *
 * Priority order:
 *   1. xtc.java.home VS Code setting
 *   2. JAVA_HOME environment variable
 *   3. SDKMAN Java 25 installation (~/.sdkman/candidates/java/25.*)
 *   4. Common JDK installation paths (macOS, Linux)
 *   5. 'java' in PATH (fallback)
 */
export function findJavaExecutable(): string {
    return fromConfig() ?? fromJavaHome() ?? fromSdkman() ?? fromCommonPaths() ?? 'java';
}

function fromConfig(): string | null {
    const configuredHome = vscode.workspace
        .getConfiguration('xtc')
        .get<string>('java.home', '')
        .trim();

    return configuredHome ? resolveJavaBin(configuredHome) : null;
}

function fromJavaHome(): string | null {
    const javaHome = process.env.JAVA_HOME;
    if (!javaHome) {
        return null;
    }

    const candidate = path.join(javaHome, 'bin', 'java');
    return isJava25OrLater(candidate) ? candidate : null;
}

function fromSdkman(): string | null {
    const sdkmanHome = process.env.SDKMAN_DIR
        ?? path.join(process.env.HOME ?? '', '.sdkman');

    return findJava25InDirectory(
        path.join(sdkmanHome, 'candidates', 'java')
    );
}

function fromCommonPaths(): string | null {
    const dirs = process.platform === 'darwin'
        ? ['/Library/Java/JavaVirtualMachines']
        : ['/usr/lib/jvm', '/usr/java'];

    for (const dir of dirs) {
        const found = findJava25InSystemDir(dir);
        if (found) {
            return found;
        }
    }
    return null;
}

function resolveJavaBin(javaHome: string): string | null {
    if (!javaHome) {
        return null;
    }
    const candidate = path.join(javaHome, 'bin', 'java');
    return fs.existsSync(candidate) ? candidate : null;
}

function tryReadDir(dir: string): string[] {
    try {
        return fs.readdirSync(dir);
    } catch {
        return [];
    }
}

/**
 * Look for Java 25+ in a SDKMAN-style directory.
 */
function findJava25InDirectory(javaDir: string): string | null {
    const entries = tryReadDir(javaDir)
        .filter(e => e.startsWith('25'))
        .sort((a, b) => b.localeCompare(a));

    for (const entry of entries) {
        const candidate = path.join(javaDir, entry, 'bin', 'java');
        if (fs.existsSync(candidate)) {
            return candidate;
        }
    }
    return null;
}

/**
 * Look for Java 25+ in system JDK directories.
 */
function findJava25InSystemDir(dir: string): string | null {
    const entries = tryReadDir(dir)
        .filter(e => /25/.test(e))
        .sort((a, b) => b.localeCompare(a));

    for (const entry of entries) {
        // macOS: /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java
        const macCandidate = path.join(dir, entry, 'Contents', 'Home', 'bin', 'java');
        if (fs.existsSync(macCandidate)) {
            return macCandidate;
        }
        // Linux: /usr/lib/jvm/java-25-openjdk/bin/java
        const linuxCandidate = path.join(dir, entry, 'bin', 'java');
        if (fs.existsSync(linuxCandidate)) {
            return linuxCandidate;
        }
    }
    return null;
}

/**
 * Check whether a Java executable is version 25 or later.
 */
function isJava25OrLater(javaPath: string): boolean {
    try {
        const output = execSync(`"${javaPath}" -version 2>&1`, { encoding: 'utf-8', timeout: 5000 });
        const match = /version "(\d+)/.exec(output);
        if (match) {
            return Number.parseInt(match[1], 10) >= 25;
        }
    } catch {
        // If we cannot determine version, do not reject it.
    }
    return false;
}

/**
 * Common JVM arguments for the XTC server processes (LSP and DAP).
 */
export function buildJvmArgs(serverJar: string, logLevel?: string): string[] {
    return [
        '--enable-native-access=ALL-UNNAMED',
        '-Dapple.awt.UIElement=true',
        '-Djava.awt.headless=true',
        `-Dxtc.logLevel=${logLevel ?? 'INFO'}`,
        '-jar', serverJar
    ];
}
