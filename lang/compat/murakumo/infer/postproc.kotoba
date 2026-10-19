;; murakumo.infer.postproc — media post-processing work the browser swarm does.
;;
;; When a native node generates an image, the useful embarrassingly-parallel
;; follow-on work — feature extraction for the gallery index (luminance,
;; histogram, a perceptual-ish signature for dedup) — can flow to the browser/
;; wasm swarm over the relay. It is exactly the tier of work join.cljc marks
;; browser-eligible: no large model resident, pure per-pixel reduction, WebGPU
;; (WGSL) or a JS fallback. This namespace is the PURE reference reduction, so
;; the WGSL kernel's output can be VERIFIED against it byte-for-byte.

(ns murakumo.infer.postproc)

(def ^:const bins 16)

(defn luma
  "Rec.601 luminance of an [r g b] pixel (0–255) → 0–255 double."
  [[r g b]]
  (+ (* 0.299 r) (* 0.587 g) (* 0.114 b)))

(defn features
  "Reduce a seq of [r g b] pixels to gallery-index features:
   {:n :mean-luma :histogram [16 bins] :signature <16-hex>}. The signature is a
   coarse luminance-histogram fingerprint — cheap dedup / near-dup detection.
   PURE: this is the contract the WGSL kernel must reproduce."
  [pixels]
  (let [n (count pixels)
        lumas (map luma pixels)
        sum (reduce + lumas)
        hist (reduce (fn [h l]
                       (update h (min (dec bins) (int (/ l (/ 256.0 bins)))) inc))
                     (vec (repeat bins 0)) lumas)
        ;; signature: each bin → 1 if above the mean-per-bin, packed to hex
        mean-bin (if (pos? n) (/ n bins) 0)
        bits (map #(if (> % mean-bin) 1 0) hist)
        sig (reduce (fn [acc [i b]] (if (pos? b) (bit-or acc (bit-shift-left 1 i)) acc))
                    0 (map-indexed vector bits))]
    {:n n
     :mean-luma (if (pos? n) (/ sum n) 0.0)
     :histogram hist
     :signature (format "%04x" sig)}))

(defn wgsl-reduce-kernel
  "The WGSL compute shader the browser worker runs — a workgroup reduction over
   an RGBA input buffer producing per-bin luminance counts. Kept here (as data)
   so it ships with the contract it satisfies. Host binds input/output buffers."
  []
  "@group(0) @binding(0) var<storage,read> pixels: array<u32>;   // packed rgba8
@group(0) @binding(1) var<storage,read_write> hist: array<atomic<u32>,16>;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let i = gid.x;
  if (i >= arrayLength(&pixels)) { return; }
  let p = pixels[i];
  let r = f32(p & 0xffu);
  let g = f32((p >> 8u) & 0xffu);
  let b = f32((p >> 16u) & 0xffu);
  let l = 0.299*r + 0.587*g + 0.114*b;
  let bin = min(15u, u32(l / 16.0));
  atomicAdd(&hist[bin], 1u);
}")

;; ── upscale: native generates small, the browser swarm enlarges ─────────────
;; The real division of labor for a render farm — a 16GB mini renders SDXL at
;; 832×1216 fast; upscaling to 2× is embarrassingly parallel per output pixel
;; and belongs on the browser swarm, not the generator.

(defn- clampi [x lo hi] (max lo (min hi x)))

(defn bilinear-2x
  "Pure reference 2× bilinear upscale of a row-major [r g b] image of size w×h.
   Returns the 2w×2h pixel vector. This is the contract the WGSL upscale kernel
   must reproduce (spot-checked by verify-upscale)."
  [pixels w h]
  (let [src (vec pixels)
        at (fn [x y] (nth src (+ (* (clampi y 0 (dec h)) w) (clampi x 0 (dec w)))))
        ow (* 2 w) oh (* 2 h)]
    (vec
     (for [oy (range oh) ox (range ow)
           :let [gx (/ (double ox) 2.0) gy (/ (double oy) 2.0)
                 x0 (int gx) y0 (int gy)
                 fx (- gx x0) fy (- gy y0)
                 lerp (fn [a b t] (+ a (* (- b a) t)))
                 mix (fn [i] (lerp (lerp (nth (at x0 y0) i) (nth (at (inc x0) y0) i) fx)
                                   (lerp (nth (at x0 (inc y0)) i) (nth (at (inc x0) (inc y0)) i) fx)
                                   fy))]]
       [(Math/round (mix 0)) (Math/round (mix 1)) (Math/round (mix 2))]))))

(defn wgsl-upscale-kernel
  "WGSL 2× bilinear upscale — one thread per OUTPUT pixel. Ships with its
   contract (bilinear-2x)."
  []
  "@group(0) @binding(0) var<storage,read> src: array<u32>;
@group(0) @binding(1) var<storage,read_write> dst: array<u32>;
@group(0) @binding(2) var<uniform> dims: vec2<u32>;   // w,h
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) gid: vec3<u32>) {
  let w = dims.x; let h = dims.y; let ow = w*2u; let oh = h*2u;
  let idx = gid.x; if (idx >= ow*oh) { return; }
  let ox = idx % ow; let oy = idx / ow;
  let gx = f32(ox)/2.0; let gy = f32(oy)/2.0;
  let x0 = u32(gx); let y0 = u32(gy);
  let x1 = min(x0+1u, w-1u); let y1 = min(y0+1u, h-1u);
  let fx = gx - f32(x0); let fy = gy - f32(y0);
  let p00 = src[y0*w+x0]; let p10 = src[y0*w+x1];
  let p01 = src[y1*w+x0]; let p11 = src[y1*w+x1];
  var out = 0u;
  for (var c=0u; c<3u; c++) {
    let s=c*8u;
    let a=f32((p00>>s)&0xffu); let b=f32((p10>>s)&0xffu);
    let cc=f32((p01>>s)&0xffu); let d=f32((p11>>s)&0xffu);
    let top=a+(b-a)*fx; let bot=cc+(d-cc)*fx; let v=u32(round(top+(bot-top)*fy));
    out = out | ((v & 0xffu) << s);
  }
  dst[idx] = out;
}")

(defn verify-upscale
  "Spot-check: does the worker's upscaled output match the reference at `samples`
   random-but-seeded positions? (Full re-check would defeat offloading; sampling
   is the proof-of-compute knob.)"
  [pixels w h claimed sample-positions]
  (let [ref (bilinear-2x pixels w h)]
    (every? (fn [p] (= (nth ref p) (nth (vec claimed) p))) sample-positions)))

(defn verify
  "Does a worker's returned histogram match the pure reference for these pixels?
   The relay/dispatcher uses this to reject a bogus result before crediting —
   the seed of proof-of-compute (a fraction of jobs are re-verified)."
  [pixels claimed-histogram]
  (= (:histogram (features pixels)) (vec claimed-histogram)))
