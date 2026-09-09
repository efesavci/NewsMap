/**
 * Offline Border Texture Generator (HIGH RESOLUTION)
 * 
 * Run once: node scripts/generate-borders.mjs
 * 
 * Uses the ORIGINAL full-resolution world.json (4.3MB, every coastline detail)
 * to render a 4096×2048 equirectangular PNG with crisp country borders.
 * 
 * The GPU doesn't care how complex the source geometry was —
 * it's just loading a flat image. All the detail is baked in for free.
 * 
 * Output: public/borders.png
 */
import { createCanvas } from 'canvas';
import { readFileSync, writeFileSync } from 'fs';
import { geoEquirectangular, geoPath } from 'd3-geo';

const WIDTH = 8192;
const HEIGHT = 4096;

// Use the ORIGINAL full-resolution GeoJSON — not the simplified one
const geoJson = JSON.parse(readFileSync('../resources/world.json', 'utf-8'));
const validFeatures = geoJson.features.filter(f => f.geometry != null);

console.log(`📐 Rendering ${validFeatures.length} features at ${WIDTH}×${HEIGHT}...`);

const canvas = createCanvas(WIDTH, HEIGHT);
const ctx = canvas.getContext('2d');

// Transparent background
ctx.clearRect(0, 0, WIDTH, HEIGHT);

// Equirectangular projection scaled to canvas
const projection = geoEquirectangular()
  .scale(WIDTH / (2 * Math.PI))
  .translate([WIDTH / 2, HEIGHT / 2]);

const path = geoPath(projection, ctx);

// Subtle, thin blue-grey borders (slightly more visible)
ctx.strokeStyle = 'rgba(148, 163, 184, 0.55)';
ctx.lineWidth = 0.5;  // Ultra-thin lines for a clean, minimal glass look
ctx.lineJoin = 'round';
ctx.lineCap = 'round';

validFeatures.forEach(feature => {
  ctx.beginPath();
  path(feature);
  ctx.stroke();
});

const buffer = canvas.toBuffer('image/png');
writeFileSync('public/borders.png', buffer);
console.log(`✅ Generated public/borders.png (${(buffer.length / 1024).toFixed(1)} KB) from full-res world.json`);
