import fs from 'node:fs/promises';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
const table = Array.from({ length: 256 }, (_, i) => { let c = i; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
function crc32(data) { let c = 0xffffffff; for (const b of data) c = table[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const bytes = Buffer.from(type); const result = Buffer.alloc(data.length + 12); result.writeUInt32BE(data.length); bytes.copy(result, 4); data.copy(result, 8); result.writeUInt32BE(crc32(Buffer.concat([bytes, data])), data.length + 8); return result; }
const polygon = [[133, 260], [213, 340], [390, 160], [352, 122], [213, 265], [171, 221]];
function inside(x, y) { let yes = false; for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) { const [xi, yi] = polygon[i], [xj, yj] = polygon[j]; if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) yes = !yes; } return yes; }
for (const size of [128, 192, 512]) {
  const scanlines = Buffer.alloc((size * 4 + 1) * size);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const color = inside((x + .5) * 512 / size, (y + .5) * 512 / size) ? [221, 246, 189] : [23, 108, 82];
    const offset = y * (size * 4 + 1) + 1 + x * 4;
    for (let c = 0; c < 3; c++) scanlines[offset + c] = color[c];
    scanlines[offset + 3] = 255;
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4); ihdr[8] = 8; ihdr[9] = 6;
  const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(scanlines)), chunk('IEND', Buffer.alloc(0))]);
  await fs.writeFile(fileURLToPath(new URL(`../icons/icon${size}.png`, import.meta.url)), png);
}
console.log('Generated application icons: 128, 192, 512');
