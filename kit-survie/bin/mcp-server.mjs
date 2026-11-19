#!/usr/bin/env node
/**
 * Universal MCP Launcher (`bin/mcp-server.mjs`)
 * Automatically locates a working Python 3.10+ interpreter (Termux Android, Linux, macOS, Windows)
 * and launches `src/offline_survival_mcp/server.py` with `PYTHONPATH=src`.
 */

import { spawn, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = resolve(__dirname, "..");
const SERVER_PY = resolve(ROOT_DIR, "src", "offline_survival_mcp", "server.py");

function findPythonBinary() {
  const candidates = [
    process.env.PYTHON_BIN,
    "python3",
    "python",
    "/data/data/com.termux/files/usr/bin/python3",
    "C:\\Program Files\\LibreOffice\\program\\python.exe",
  ].filter(Boolean);

  for (const cmd of candidates) {
    try {
      if (cmd.includes("\\") || cmd.includes("/")) {
        if (!existsSync(cmd)) continue;
      }
      const check = spawnSync(cmd, ["-c", "import sys; print(sys.version_info.major)"], {
        encoding: "utf-8",
        timeout: 3000,
      });
      if (check.status === 0 && check.stdout.trim() === "3") {
        return cmd;
      }
    } catch {
      // Try next candidate
    }
  }
  throw new Error("Aucun interpréteur Python 3 trouvé. Installez Python 3 (ex: `pkg install python` sous Termux).");
}

const pythonBin = findPythonBinary();
const child = spawn(pythonBin, [SERVER_PY, ...process.argv.slice(2)], {
  stdio: "inherit",
  env: {
    ...process.env,
    PYTHONPATH: resolve(ROOT_DIR, "src"),
    PYTHONIOENCODING: "utf-8",
  },
});

child.on("exit", (code) => {
  process.exit(code ?? 0);
});

