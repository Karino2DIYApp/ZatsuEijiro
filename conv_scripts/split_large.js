const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');
const { pipeline } = require('node:stream/promises');

const outputDir = path.resolve(process.argv[2] ?? 'split');
const sizeLimit = 1024 * 1024;
const maxOpenFiles = 64;

async function splitFile(filePath, prefixLength, tempDir) {
  const base = path.basename(filePath, '.txt');
  const openFiles = new Map();
  const initializedFiles = new Set();
  let written = 0;

  function getFileDescriptor(key) {
    if (openFiles.has(key)) {
      const fd = openFiles.get(key);
      openFiles.delete(key);
      openFiles.set(key, fd);
      return fd;
    }

    if (openFiles.size >= maxOpenFiles) {
      const oldestKey = openFiles.keys().next().value;
      fs.closeSync(openFiles.get(oldestKey));
      openFiles.delete(oldestKey);
    }

    const targetPath = path.join(tempDir, `${key}.txt`);
    const flags = initializedFiles.has(key) ? 'a' : 'w';
    const fd = fs.openSync(targetPath, flags);
    initializedFiles.add(key);
    openFiles.set(key, fd);
    return fd;
  }

  function writeLine(fd, text) {
    const buffer = Buffer.from(`${text}\n`, 'utf8');
    let offset = 0;
    while (offset < buffer.length) {
      offset += fs.writeSync(fd, buffer, offset, buffer.length - offset);
    }
  }

  const input = fs.createReadStream(filePath, { encoding: 'utf8' });
  const lines = readline.createInterface({ input, crlfDelay: Infinity });

  try {
    for await (const line of lines) {
      const entry = line.startsWith('■') ? line.slice(1) : '';
      const separator = entry.indexOf(' : ');
      const headword = (separator === -1 ? entry : entry.slice(0, separator)).trimStart();
      const characters = Array.from(headword);
      let key = base;

      if (characters.length > prefixLength
        && characters.slice(0, prefixLength).join('').toLowerCase() === base
        && /^[a-z]$/i.test(characters[prefixLength])) {
        key += characters[prefixLength].toLowerCase();
      }

      writeLine(getFileDescriptor(key), line);
      written++;
    }
  } finally {
    for (const fd of openFiles.values()) {
      fs.closeSync(fd);
    }
  }

  let retainedSource = false;
  for (const name of fs.readdirSync(tempDir)) {
    const tempPath = path.join(tempDir, name);
    const targetPath = path.join(outputDir, name);

    if (path.basename(name, '.txt') === base) {
      if (fs.existsSync(tempPath)) {
        fs.renameSync(tempPath, filePath);
        retainedSource = true;
      } else {
        fs.unlinkSync(filePath);
      }
      continue;
    }

    if (fs.existsSync(targetPath)) {
      await pipeline(
        fs.createReadStream(tempPath),
        fs.createWriteStream(targetPath, { flags: 'a' }),
      );
      fs.unlinkSync(tempPath);
    } else {
      fs.renameSync(tempPath, targetPath);
    }
  }

  if (!retainedSource && fs.existsSync(filePath)) {
    fs.unlinkSync(filePath);
  }

  return written;
}

async function main() {
  if (!fs.existsSync(outputDir) || !fs.statSync(outputDir).isDirectory()) {
    throw new Error(`Split directory does not exist: ${outputDir}`);
  }

  const tempDir = fs.mkdtempSync(path.join(outputDir, '.split-large-'));
  let totalWritten = 0;
  let filesSplit = 0;

  try {
    for (const prefixLength of [2, 3]) {
      const candidates = fs.readdirSync(outputDir)
        .filter((name) => new RegExp(`^[a-z]{${prefixLength}}\\.txt$`).test(name))
        .map((name) => path.join(outputDir, name))
        .filter((filePath) => fs.statSync(filePath).size >= sizeLimit);

      for (const filePath of candidates) {
        totalWritten += await splitFile(filePath, prefixLength, tempDir);
        filesSplit++;
      }
    }
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }

  console.log(`Split ${filesSplit} files; processed ${totalWritten} entries in ${outputDir}.`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
