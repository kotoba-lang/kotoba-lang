(ns kotoba.compiler.accelerator
  "Typed, bounded accelerator KIR and deterministic WGSL/CUDA lowering."
  (:require [kotoba.artifact.core :as artifact]))

(def kir-format :kotoba.accelerator-kir/v1)
(def artifact-format :kotoba.gpu-artifact/v1)
(def targets #{:wgsl-v1 :cuda-v1 :msl-v1})
(def dtypes #{:f32})
(def ewise-operators #{:add :sub :mul :div})
(def reduction-operators #{:sum :max :min})
(def max-workgroup-size 1024)
(def max-static-elements 1073741824)

(defn kernel
  [name op operator {:keys [workgroup-size] :or {workgroup-size 256}}]
  {:format kir-format :kernel/name name :kernel/op op :kernel/operator operator
   :kernel/dtype :f32 :kernel/index-type :u32 :kernel/workgroup-size workgroup-size
   :kernel/bounds {:elements :runtime-u32 :max-elements max-static-elements}
   :kernel/effects #{:gpu/storage-read :gpu/storage-write}})

(defn validation-errors [kir]
  (vec
   (keep identity
         [(when-not (= kir-format (:format kir)) {:error :invalid-format})
          (when-not (and (string? (:kernel/name kir))
                         (re-matches #"[A-Za-z_][A-Za-z0-9_]*" (:kernel/name kir)))
            {:error :invalid-kernel-name})
          (when-not (#{:ewise :reduce} (:kernel/op kir)) {:error :invalid-kernel-op})
          (when-not (case (:kernel/op kir)
                      :ewise (ewise-operators (:kernel/operator kir))
                      :reduce (reduction-operators (:kernel/operator kir)) false)
            {:error :invalid-operator :operator (:kernel/operator kir)})
          (when-not (dtypes (:kernel/dtype kir)) {:error :invalid-dtype})
          (when-not (= :u32 (:kernel/index-type kir)) {:error :invalid-index-type})
          (when-not (and (pos-int? (:kernel/workgroup-size kir))
                         (zero? (bit-and (:kernel/workgroup-size kir)
                                         (dec (:kernel/workgroup-size kir))))
                         (<= (:kernel/workgroup-size kir) max-workgroup-size))
            {:error :invalid-workgroup-size})
          (when-not (= {:elements :runtime-u32 :max-elements max-static-elements}
                       (:kernel/bounds kir)) {:error :invalid-resource-bounds})
          (when-not (= #{:gpu/storage-read :gpu/storage-write} (:kernel/effects kir))
            {:error :invalid-effects})])))

(defn validate! [kir]
  (let [errors (validation-errors kir)]
    (when (seq errors) (throw (ex-info "invalid accelerator KIR" {:errors errors}))) kir))

(def ^:private expression
  {:add ["x[i] + y[i]" "x[i] + y[i]"] :sub ["x[i] - y[i]" "x[i] - y[i]"]
   :mul ["x[i] * y[i]" "x[i] * y[i]"] :div ["x[i] / y[i]" "x[i] / y[i]"]})

(defn- emit-wgsl [kir]
  (let [wg (:kernel/workgroup-size kir) op (:kernel/op kir) operator (:kernel/operator kir)]
    (case op
      :ewise
      (str "struct N { value: u32 };\n@group(0) @binding(0) var<storage,read> x: array<f32>;\n"
           "@group(0) @binding(1) var<storage,read> y: array<f32>;\n"
           "@group(0) @binding(2) var<storage,read_write> z: array<f32>;\n"
           "@group(0) @binding(3) var<uniform> n: N;\n"
           "@compute @workgroup_size(" wg ") fn main(@builtin(global_invocation_id) gid: vec3<u32>) {\n"
           "  let i=gid.x; if(i<n.value){ z[i]=" (first (expression operator)) "; }\n}\n")
      :reduce
      (let [identity ({:sum "0.0" :max "-3.402823466e+38" :min "3.402823466e+38"} operator)
            combine ({:sum "a+b" :max "max(a,b)" :min "min(a,b)"} operator)]
        (str "struct N { value: u32 };\n@group(0) @binding(0) var<storage,read> x: array<f32>;\n"
             "@group(0) @binding(1) var<storage,read_write> parts: array<f32>;\n"
             "@group(0) @binding(2) var<uniform> n: N;\nvar<workgroup> scratch: array<f32," wg ">;\n"
             "fn combine(a:f32,b:f32)->f32{return " combine ";}\n"
             "@compute @workgroup_size(" wg ") fn main(@builtin(global_invocation_id) gid:vec3<u32>,"
             "@builtin(local_invocation_id) lid:vec3<u32>,@builtin(workgroup_id) wid:vec3<u32>){\n"
             " let i=gid.x; scratch[lid.x]=select(" identity ",x[i],i<n.value); workgroupBarrier();\n"
             " var stride:u32=" (/ wg 2) "u; loop { if(stride==0u){break;} if(lid.x<stride){scratch[lid.x]=combine(scratch[lid.x],scratch[lid.x+stride]);} workgroupBarrier(); stride=stride/2u;}\n"
             " if(lid.x==0u){parts[wid.x]=scratch[0];}\n}\n")))))

(defn- emit-cuda [kir]
  (let [op (:kernel/op kir) operator (:kernel/operator kir) name (:kernel/name kir)]
    (case op
      :ewise
      (str "extern \"C\" __global__ void " name "(const float* x,const float* y,float* z,unsigned int n){\n"
           " unsigned int i=blockIdx.x*blockDim.x+threadIdx.x; if(i<n) z[i]=" (second (expression operator)) ";\n}\n")
      :reduce
      (let [identity ({:sum "0.0f" :max "-3.402823466e+38f" :min "3.402823466e+38f"} operator)
            combine ({:sum "a+b" :max "fmaxf(a,b)" :min "fminf(a,b)"} operator)]
        (str "extern \"C\" __global__ void " name "(const float* x,float* parts,unsigned int n){\n"
             " extern __shared__ float s[]; unsigned int i=blockIdx.x*blockDim.x+threadIdx.x; unsigned int l=threadIdx.x;\n"
             " s[l]=i<n?x[i]:" identity "; __syncthreads();\n"
             " for(unsigned int d=blockDim.x/2;d>0;d>>=1){if(l<d){float a=s[l],b=s[l+d];s[l]=" combine ";}__syncthreads();}\n"
             " if(l==0)parts[blockIdx.x]=s[0];\n}\n")))))

(defn- emit-msl [kir]
  (let [op (:kernel/op kir) operator (:kernel/operator kir) name (:kernel/name kir)]
    (case op
      :ewise
      (str "#include <metal_stdlib>\nusing namespace metal;\n"
           "kernel void " name "(device const float* x [[buffer(0)]],device const float* y [[buffer(1)]],"
           "device float* z [[buffer(2)]],constant uint& n [[buffer(3)]],uint i [[thread_position_in_grid]]){\n"
           " if(i<n) z[i]=" (second (expression operator)) ";\n}\n")
      :reduce
      (let [identity ({:sum "0.0f" :max "-3.402823466e+38f" :min "3.402823466e+38f"} operator)
            combine ({:sum "a+b" :max "max(a,b)" :min "min(a,b)"} operator)]
        (str "#include <metal_stdlib>\nusing namespace metal;\n"
             "kernel void " name "(device const float* x [[buffer(0)]],device float* parts [[buffer(1)]],"
             "constant uint& n [[buffer(2)]],threadgroup float* s [[threadgroup(0)]],"
             "uint i [[thread_position_in_grid]],uint l [[thread_index_in_threadgroup]],"
             "uint g [[threadgroup_position_in_grid]],uint width [[threads_per_threadgroup]]){\n"
             " s[l]=i<n?x[i]:" identity "; threadgroup_barrier(mem_flags::mem_threadgroup);\n"
             " for(uint d=width/2;d>0;d>>=1){if(l<d){float a=s[l],b=s[l+d];s[l]=" combine ";}threadgroup_barrier(mem_flags::mem_threadgroup);}\n"
             " if(l==0)parts[g]=s[0];\n}\n")))))

(defn lower [kir target]
  (validate! kir)
  (when-not (targets target) (throw (ex-info "unsupported accelerator target" {:target target :supported targets})))
  ((case target :wgsl-v1 emit-wgsl :cuda-v1 emit-cuda :msl-v1 emit-msl) kir))

(defn compile-kernel [kir target]
  (let [code (lower kir target)
        payload {:format artifact-format :target target :kir kir
                 :kir-sha256 (artifact/sha256 kir) :code code
                 :code-sha256 (artifact/sha256 code)
                 :limits {:max-elements max-static-elements
                          :workgroup-size (:kernel/workgroup-size kir)}}]
    (artifact/seal payload)))

(defn verify-artifact! [compiled]
  (when-not (and (= artifact-format (:format compiled)) (artifact/valid-seal? compiled)
                 (targets (:target compiled)))
    (throw (ex-info "invalid accelerator artifact envelope" {})))
  (validate! (:kir compiled))
  (let [expected-code (lower (:kir compiled) (:target compiled))]
    (when-not (and (= (:kir-sha256 compiled) (artifact/sha256 (:kir compiled)))
                   (= (:code-sha256 compiled) (artifact/sha256 expected-code))
                   (= (:code compiled) expected-code))
      (throw (ex-info "accelerator artifact regeneration mismatch" {}))))
  compiled)
