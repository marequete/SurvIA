#!/usr/bin/env node
/**
 * Zero-Dependency GitHub Repository Publisher (`scripts/publish_to_github.mjs`)
 * Target Repository: https://github.com/Soonz-38/kit-survie.git
 *
 * Supports:
 * - Pushing to an existing OR completely empty GitHub repository (`Soonz-38/kit-survie`)
 *   via the GitHub REST API (no local `git.exe` required).
 * - Using `GITHUB_TOKEN` / `GH_TOKEN` environment variable.
 */

import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = resolve(__dirname, "..");

const DEFAULT_TARGET = "Soonz-38/kit-survie";
const IGNORED_DIRS = new Set([".git", "__pycache__", "node_modules", ".venv", "venv"]);
const IGNORED_EXTS = new Set([".pyc", ".pyo", ".zim", ".pmtiles", ".mwm", ".gguf"]);

function collectFiles(dir, baseDir = ROOT_DIR) {
  const entries = readdirSync(dir);
  const files = [];
  for (const name of entries) {
    if (IGNORED_DIRS.has(name)) continue;
    const full = join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) {
      files.push(...collectFiles(full, baseDir));
    } else if (st.isFile()) {
      const ext = name.includes(".") ? name.slice(name.lastIndexOf(".")) : "";
      if (IGNORED_EXTS.has(ext)) continue;
      const rel = relative(baseDir, full).replace(/\\/g, "/");
      files.push({ rel, full });
    }
  }
  return files;
}

async function ghRequest(token, path, method = "GET", body = undefined) {
  const res = await fetch(`https://api.github.com${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      "Content-Type": "application/json",
      "User-Agent": "kit-survie-mcp-publisher/1.0",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { raw: text };
  }
  if (!res.ok) {
    const err = new Error(`GitHub API ${method} ${path} -> ${res.status}: ${data.message || text}`);
    err.status = res.status;
    throw err;
  }
  return data;
}

function parseTarget(arg) {
  const cleaned = (arg || DEFAULT_TARGET)
    .replace(/^https?:\/\/github\.com\//i, "")
    .replace(/\.git$/i, "")
    .replace(/^\/+|\/+$/g, "");
  if (cleaned.includes("/")) {
    const [owner, repo] = cleaned.split("/");
    return { owner, repo };
  }
  return { owner: "Soonz-38", repo: cleaned };
}

async function ensureMainBranchInitialized(token, owner, repo, branch = "main") {
  try {
    const refData = await ghRequest(token, `/repos/${owner}/${repo}/git/ref/heads/${branch}`);
    return refData.object.sha;
  } catch (err) {
    if (err.status === 404 || err.status === 409) {
      console.log(`[Init] Le dépôt ${owner}/${repo} est vide. Initialisation de la branche '${branch}'...`);
      const readmeContent = readFileSync(join(ROOT_DIR, "README.md")).toString("base64");
      const putRes = await ghRequest(token, `/repos/${owner}/${repo}/contents/README.md`, "PUT", {
        message: "chore: initialize kit-survie repository",
        content: readmeContent,
        branch,
      });
      return putRes.commit.sha;
    }
    throw err;
  }
}

async function main() {
  const { owner, repo } = parseTarget(process.argv[2]);
  const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;

  const files = collectFiles(ROOT_DIR);
  console.log(`[1/4] ${files.length} fichiers prêts pour https://github.com/${owner}/${repo}.git`);

  if (!token) {
    console.error("Aucun token GITHUB_TOKEN détecté dans l'environnement.");
    process.exit(2);
  }

  let repoInfo;
  try {
    repoInfo = await ghRequest(token, `/repos/${owner}/${repo}`);
    console.log(`[2/4] Dépôt cible vérifié : ${repoInfo.html_url}`);
  } catch (err) {
    if (err.status === 404) {
      console.log(`[2/4] Création du dépôt ${owner}/${repo} sur GitHub...`);
      repoInfo = await ghRequest(token, "/user/repos", "POST", {
        name: repo,
        description:
          "Serveur MCP 100% Hors-Ligne unifiant Kiwix ZIM, MedGemma-FA, Survival-Station et Organic Maps pour Android (Gemma 4 E4B IT) & PC",
        private: false,
        auto_init: true,
      });
    } else {
      throw err;
    }
  }

  const branch = repoInfo.default_branch || "main";
  const baseCommitSha = await ensureMainBranchInitialized(token, owner, repo, branch);

  console.log(`[3/4] Téléversement des ${files.length} fichiers sur ${owner}/${repo} (branche ${branch})...`);
  const treeItems = [];
  for (const f of files) {
    const raw = readFileSync(f.full);
    const blob = await ghRequest(token, `/repos/${owner}/${repo}/git/blobs`, "POST", {
      content: raw.toString("base64"),
      encoding: "base64",
    });
    treeItems.push({
      path: f.rel,
      mode: f.rel.endsWith(".sh") || f.rel.endsWith(".mjs") ? "100755" : "100644",
      type: "blob",
      sha: blob.sha,
    });
  }

  const newTree = await ghRequest(token, `/repos/${owner}/${repo}/git/trees`, "POST", {
    base_tree: baseCommitSha,
    tree: treeItems,
  });

  const newCommit = await ghRequest(token, `/repos/${owner}/${repo}/git/commits`, "POST", {
    message:
      "feat: assemble kiwix-wiki-mcp-server, MedGemmaImpactChallenge, survival-station & organicmaps into unified offline MCP server for Android Gemma 4 E4B IT",
    tree: newTree.sha,
    parents: [baseCommitSha],
  });

  await ghRequest(token, `/repos/${owner}/${repo}/git/refs/heads/${branch}`, "PATCH", {
    sha: newCommit.sha,
    force: true,
  });

  console.log(`[4/4] Importation terminée avec succès sur : https://github.com/${owner}/${repo}`);
}

main().catch((err) => {
  console.error("Erreur :", err.message);
  process.exit(1);
});
