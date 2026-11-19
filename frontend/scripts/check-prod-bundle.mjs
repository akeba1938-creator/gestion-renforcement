import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { extname, join, relative, resolve } from 'node:path';

const DIST_DIR = resolve(process.cwd(), 'dist', 'ecole-web');
const INSPECTED_EXTENSIONS = new Set(['.js', '.mjs', '.html', '.css', '.map']);
const FORBIDDEN_PATTERNS = ['demo-memory-token', 'theme-preview', 'dev-login'];

function collectFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const fullPath = join(dir, entry);
    const stats = statSync(fullPath);
    if (stats.isDirectory()) {
      results.push(...collectFiles(fullPath));
    } else if (stats.isFile()) {
      results.push(fullPath);
    }
  }
  return results;
}

if (!existsSync(DIST_DIR)) {
  console.error(`Erreur : le dossier de build est introuvable (${DIST_DIR}).`);
  process.exit(1);
}

const allFiles = collectFiles(DIST_DIR);
if (allFiles.length === 0) {
  console.error(`Erreur : le dossier de build est vide (${DIST_DIR}).`);
  process.exit(1);
}

const inspectedFiles = allFiles.filter((filePath) =>
  INSPECTED_EXTENSIONS.has(extname(filePath).toLowerCase())
);

if (inspectedFiles.length === 0) {
  console.error(`Erreur : aucun fichier js/html/css/map trouvé dans ${DIST_DIR}.`);
  process.exit(1);
}

const violations = [];

for (const filePath of inspectedFiles) {
  const content = readFileSync(filePath, 'utf8');
  for (const pattern of FORBIDDEN_PATTERNS) {
    if (content.includes(pattern)) {
      violations.push({
        file: relative(process.cwd(), filePath),
        pattern,
      });
    }
  }
}

if (violations.length > 0) {
  console.error('Échec check:prod-bundle — artefacts de développement détectés dans dist/ :');
  for (const violation of violations) {
    console.error(`  - ${violation.file} contient "${violation.pattern}"`);
  }
  process.exit(1);
}

console.log(
  `check:prod-bundle OK — ${inspectedFiles.length} fichiers inspectés dans dist/ecole-web, aucune trace de ${FORBIDDEN_PATTERNS.join(', ')}.`
);
