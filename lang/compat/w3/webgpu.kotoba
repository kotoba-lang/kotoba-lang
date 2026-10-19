(ns w3.webgpu
  "Raw W3C WebGPU JS API — thin ClojureScript wrapper, one function per spec
   call. No render-graph, no EDN vocabulary, no scene/material opinions: this
   is the binding layer other repos (kami-webgpu) build on, the same way
   org-khronos-glb/org-materialx/org-openusd are the raw-spec layer under
   kami-engine's own EDN domain vocabulary.

   Descriptors (desc/opts args below) are plain JS objects (`#js {...}`),
   built by the caller with the spec's own camelCase keys — this wrapper does
   not translate Clojure maps into descriptors, since WebGPU's camelCase,
   multi-word keys (depthWriteEnabled, shaderLocation, colorAttachments, ...)
   don't round-trip through a kebab-case Clojure map without a translation
   layer this repo deliberately doesn't own. kami-webgpu's render-IR/EDN is
   that layer, one level up.

   https://www.w3.org/TR/webgpu/"
  (:require [clojure.string :as str]))

(defn gpu
  "js/navigator.gpu, or nil if the browser has no WebGPU implementation."
  []
  (.-gpu js/navigator))

(defn supported? []
  (some? (gpu)))

(defn request-adapter!
  "-> Promise<GPUAdapter|nil>. opts, if given, is a GPURequestAdapterOptions JS object."
  ([] (request-adapter! nil))
  ([opts] (let [^js gpu-api (gpu)] (.requestAdapter gpu-api opts))))

(defn request-device!
  "-> Promise<GPUDevice>. opts, if given, is a GPUDeviceDescriptor JS object."
  ([adapter] (request-device! adapter nil))
  ([^js adapter opts] (.requestDevice adapter opts)))

(defn preferred-canvas-format []
  (let [^js gpu-api (gpu)] (.getPreferredCanvasFormat gpu-api)))

(defn get-context
  "canvas.getContext(\"webgpu\") -> GPUCanvasContext, or nil."
  [^js canvas]
  (.getContext canvas "webgpu"))

(defn configure-context!
  "ctx.configure(desc) — desc is a GPUCanvasConfiguration JS object
   (must include :device/:format, spelled `device`/`format` as JS keys)."
  [^js ctx desc]
  (.configure ctx desc))

(defn- ->CONST [flag]
  (-> flag name (str/replace "-" "_") str/upper-case))

(defn buffer-usage
  "js/GPUBufferUsage.<FLAG>, e.g. (buffer-usage :copy-dst) -> GPUBufferUsage.COPY_DST."
  [flag]
  (aget js/GPUBufferUsage (->CONST flag)))

(defn texture-usage
  "js/GPUTextureUsage.<FLAG>, e.g. (texture-usage :render-attachment)."
  [flag]
  (aget js/GPUTextureUsage (->CONST flag)))

(defn create-buffer!
  "device.createBuffer(desc), desc: GPUBufferDescriptor JS object."
  [^js device desc]
  (.createBuffer device desc))

(defn destroy-buffer!
  "buffer.destroy() — releases the GPU-side allocation. Call before dropping
  the last reference to a buffer you're replacing (e.g. growing a dynamically
  resized instance buffer), not on one still in use by a pending submission."
  [^js buffer]
  (.destroy buffer))

(defn write-buffer!
  "queue.writeBuffer(buffer, offset, data). data must already be a typed array."
  ([queue buffer data] (write-buffer! queue buffer 0 data))
  ([^js queue buffer offset data] (.writeBuffer queue buffer offset data)))

(defn device-queue
  "device.queue. Property access remains confined to the raw W3C binding."
  [^js device]
  (.-queue device))

(defn write-texture!
  "queue.writeTexture(destination, data, layout, size)."
  [^js queue destination data layout size]
  (.writeTexture queue destination data layout size))

(defn copy-external-image-to-texture!
  "queue.copyExternalImageToTexture(source, destination, size)."
  [^js queue source destination size]
  (.copyExternalImageToTexture queue source destination size))

(defn create-texture!
  "device.createTexture(desc), desc: GPUTextureDescriptor JS object."
  [^js device desc]
  (.createTexture device desc))

(defn destroy-texture!
  "texture.destroy() — releases the GPU-side allocation. Same contract as
  destroy-buffer!: call when replacing a texture (e.g. a depth attachment
  recreated on canvas resize), not on one still bound to a pending submission."
  [^js texture]
  (.destroy texture))

(defn create-view
  "texture.createView(desc?), desc: GPUTextureViewDescriptor JS object."
  ([texture] (create-view texture nil))
  ([^js texture desc] (.createView texture desc)))

(defn create-sampler!
  [^js device desc]
  (.createSampler device desc))

(defn create-shader-module!
  "device.createShaderModule(desc), desc: #js {:code <wgsl-string>}."
  [^js device desc]
  (.createShaderModule device desc))

(defn create-render-pipeline!
  [^js device desc]
  (.createRenderPipeline device desc))

(defn get-bind-group-layout
  [^js pipeline index]
  (.getBindGroupLayout pipeline index))

(defn create-bind-group!
  [^js device desc]
  (.createBindGroup device desc))

(defn create-command-encoder!
  ([device] (create-command-encoder! device nil))
  ([^js device desc] (.createCommandEncoder device desc)))

(defn begin-render-pass!
  [^js encoder desc]
  (.beginRenderPass encoder desc))

(defn create-render-bundle-encoder!
  "device.createRenderBundleEncoder(desc) -> GPURenderBundleEncoder."
  [^js device desc]
  (.createRenderBundleEncoder device desc))

(defn execute-bundles!
  "pass.executeBundles(bundles). Render bundles retain referenced resources
   and may be replayed across frames while those resources remain valid."
  [^js pass bundles]
  (.executeBundles pass (into-array bundles)))

(defn set-pipeline! [^js pass pipeline] (.setPipeline pass pipeline))
(defn set-bind-group! [^js pass index bind-group] (.setBindGroup pass index bind-group))
(defn set-vertex-buffer!
  ([^js pass slot buffer] (.setVertexBuffer pass slot buffer))
  ([^js pass slot buffer offset] (.setVertexBuffer pass slot buffer offset)))
(defn set-index-buffer!
  [^js pass buffer format]
  (.setIndexBuffer pass buffer format))
(defn draw-indexed!
  ([^js pass index-count] (.drawIndexed pass index-count))
  ([^js pass index-count instance-count] (.drawIndexed pass index-count instance-count))
  ([^js pass index-count instance-count first-index] (.drawIndexed pass index-count instance-count first-index))
  ([^js pass index-count instance-count first-index base-vertex]
   (.drawIndexed pass index-count instance-count first-index base-vertex))
  ([^js pass index-count instance-count first-index base-vertex first-instance]
   (.drawIndexed pass index-count instance-count first-index base-vertex first-instance)))
(defn draw!
  "pass.draw(vertexCount, instanceCount?, firstVertex?, firstInstance?). Used by
   fullscreen triangles and non-indexed procedural geometry."
  ([^js pass vertex-count] (.draw pass vertex-count))
  ([^js pass vertex-count instance-count] (.draw pass vertex-count instance-count))
  ([^js pass vertex-count instance-count first-vertex]
   (.draw pass vertex-count instance-count first-vertex))
  ([^js pass vertex-count instance-count first-vertex first-instance]
   (.draw pass vertex-count instance-count first-vertex first-instance)))
(defn end-pass! [^js pass] (.end pass))

(defn finish!
  "command-encoder.finish() -> GPUCommandBuffer."
  ([^js encoder] (.finish encoder))
  ([^js encoder desc] (.finish encoder desc)))

(defn finish-render-bundle!
  "render-bundle-encoder.finish() -> GPURenderBundle."
  ([^js encoder] (.finish encoder))
  ([^js encoder desc] (.finish encoder desc)))

(defn submit!
  "queue.submit([...command-buffers])."
  [^js queue command-buffers]
  (.submit queue (into-array command-buffers)))

(defn current-texture
  "ctx.getCurrentTexture()."
  [^js ctx]
  (.getCurrentTexture ctx))
