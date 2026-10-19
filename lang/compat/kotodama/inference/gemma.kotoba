(ns kotodama.inference.gemma
  "Gemma model contracts for the torch-clj + num-clj portable runtime.

  This namespace is data only. It names the model-family facts and lowering ops
  that a direct runtime must satisfy before a Gemma GGUF artifact can be decoded
  without using an external model server."
  (:require [kotodama.inference.runtime :as rt]
            [torch.model :as torch]))

(def gemma4-e4b-expected
  {:kotodama/model "gemma4:e4b"
   :kotodama/format "gguf"
   :kotodama/family "gemma4"
   :kotodama/digest "c6eb396dbd5992bbe3f5cdb947e8bbc0ee413d7c17e2beaae69f5d569cf982eb"
   :kotodama/parameter-size "8.0B"
   :kotodama/quantization "Q4_K_M"
   :gguf/architecture "gemma4"
   :gguf/file-type 15
   :gguf/quantization-version 2
   :gguf/tensor-count 2131
   :gemma4/block-count 42
   :gemma4/context-length 131072
   :gemma4/embedding-length 2560
   :gemma4/feed-forward-length 10240
   :gemma4/attention-head-count 8
   :gemma4/attention-head-count-kv 2
   :gemma4/attention-key-length 512
   :gemma4/attention-value-length 512
   :gemma4/attention-sliding-window 512
   :gemma4/rope-dimension-count 512
   :gemma4/rope-dimension-count-swa 256
   :gemma4/rope-freq-base 1000000.0
   :gemma4/rope-freq-base-swa 10000.0
   :tokenizer/model "llama"
   :tokenizer/pre "gemma4"
   :tokenizer/bos-token-id 2
   :tokenizer/eos-token-id 1
   :gguf/tensor-type-counts {0 1501
                             1 116
                             12 339
                             14 41
                             30 134}
   :gguf/required-tensors {"blk.0.attn_k.weight" {:shape [2560 512] :type 12}
                           "blk.0.attn_output.weight" {:shape [2048 2560] :type 12}
                           "blk.0.attn_q.weight" {:shape [2560 2048] :type 12}
                           "blk.0.attn_v.weight" {:shape [2560 512] :type 14}
                           "blk.0.ffn_down.weight" {:shape [10240 2560] :type 14}
                           "blk.0.ffn_gate.weight" {:shape [2560 10240] :type 12}
                           "blk.0.ffn_norm.weight" {:shape [2560] :type 0}
                           "blk.0.ffn_up.weight" {:shape [2560 10240] :type 12}
                           "blk.41.attn_q.weight" {:shape [2560 4096] :type 12}
                           "blk.41.ffn_up.weight" {:shape [2560 10240] :type 12}
                           "output_norm.weight" {:shape [2560] :type 0}
                           "token_embd.weight" {:shape [2560 262144] :type 14}}
   :gguf/expected-tensor-spans {"blk.0.attn_k.weight" 737280
                                "blk.0.attn_output.weight" 2949120
                                "blk.0.attn_q.weight" 2949120
                                "blk.0.attn_v.weight" 1075200
                                "blk.0.ffn_down.weight" 21504000
                                "blk.0.ffn_gate.weight" 14745600
                                "blk.0.ffn_norm.weight" 10240
                                "blk.0.ffn_up.weight" 14745600
                                "blk.41.attn_q.weight" 5898240
                                "blk.41.ffn_up.weight" 14745600
                                "output_norm.weight" 10240
                                "token_embd.weight" 550502400}
   :gguf/payload-prefix-hex {"blk.0.attn_k.weight" "5e070814e3f2e7fce6e3eaff6fe69da64079ab55308227253e2f42662e457875"
                             "blk.0.attn_output.weight" "b509b915bff2f1e8bfaaa9a66cb947c7b07a894846088774a5b6558a58777666"
                             "blk.0.attn_q.weight" "930c4216bfb999a6ffb1a6ae8541d2716447335755774738486358251746f167"
                             "blk.0.attn_v.weight" "64376d44b29a10506ca714872b9e8333139732509b7b0d77277df320043c8e9b"
                             "blk.0.ffn_down.weight" "0c5578f6e28073a1a3a6705e5f8fad0696f76e8ed5f61d399e0f622a181d31c8"
                             "blk.0.ffn_gate.weight" "5908d413f6f2fff7f1b2f7ffb9b42e8074a579f3b2bee895f6a74a05b4c35847"
                             "blk.0.ffn_norm.weight" "0000d3400000d04000006d3f00002f3f0000bc400000ce400000bb4000008740"
                             "blk.0.ffn_up.weight" "c2080214ebbfb1a99d7fb4a748ca7fe7b7459189b44367873a6897a55fb188b5"
                             "blk.41.attn_q.weight" "760d8818e5aca7e1aabfa8e16188523f78fd70e84a79c957a507998b738896c7"
                             "blk.41.ffn_up.weight" "ce08f414bfe4eaeabfedeba7ff6871f73cc7e90ac9abbda859d989b6e97ea9f7"
                             "output_norm.weight" "0000fd4000000441000084400000f24000000f4100000d410000f34000000d41"
                             "token_embd.weight" "21152b29477c66e22a049aea4de49eaaab0b1b95be841c233dd4689dc521ff5a"}
   :gguf/f32-samples {"blk.0.ffn_norm.weight" [6.59375 6.5 0.92578125 0.68359375
                                                5.875 6.4375 5.84375 4.21875]
                       "output_norm.weight" [7.90625 8.25 4.125 7.5625
                                             8.9375 8.8125 7.59375 8.8125]}
   :gguf/q4-k-samples {"blk.0.attn_k.weight" {:block 0
                                               :value-count 256
                                               :sample [-0.037399 -0.001989 0.00588 -0.017727
                                                        -0.037399 -0.02953 -0.009858 -0.017727
                                                        0.017684 0.021618 -0.02953 -0.013792
                                                        0.017684 -0.017727 -0.005923 -0.017727]
                                               :sum -0.685832
                                               :min -0.062004
                                               :max 0.053085}
                       "blk.0.attn_output.weight" {:block 0
                                                    :value-count 256
                                                    :sample [-0.088019 0.021704 0.010732 -2.4E-4
                                                             -0.022185 -2.4E-4 -0.011213 -0.04413
                                                             -0.033157 -0.022185 -0.033157 0.021704
                                                             -2.4E-4 -0.011213 -0.022185 -0.022185]
                                                    :sum -0.189283
                                                    :min -0.088019
                                                    :max 0.088834}
                       "blk.0.attn_q.weight" {:block 0
                                               :value-count 256
                                               :sample [-0.025895 0.026871 -0.043484 0.026871
                                                        -0.008306 0.026871 0.026871 0.04446
                                                        0.04446 -0.043484 0.04446 -0.008306
                                                        0.026871 0.009283 -0.078662 0.026871]
                                               :sum 0.216478
                                               :min -0.096251
                                               :max 0.167582}
                       "blk.0.ffn_gate.weight" {:block 0
                                                :value-count 256
                                                :sample [-0.018165 -0.011 0.017659 -0.025329
                                                         -0.032494 0.053483 0.010494 -0.011
                                                         -0.003835 0.00333 0.024824 -0.011
                                                         -0.018165 -0.025329 0.010494 0.00333]
                                                :sum -0.285571
                                                :min -0.060202
                                                :max 0.075613}
                       "blk.0.ffn_up.weight" {:block 0
                                              :value-count 256
                                              :sample [0.015329 0.002842 -0.022132 0.027816
                                                       -0.003402 -0.009645 0.015329 0.015329
                                                       0.034059 0.021572 0.015329 0.002842
                                                       0.065276 -0.022132 0.021572 0.002842]
                                              :sum 0.953908
                                              :min -0.061644
                                              :max 0.08674}
                       "blk.41.attn_q.weight" {:block 0
                                                :value-count 256
                                                :sample [0.005733 0.067396 -0.092926 0.005733
                                                         0.030398 0.018066 0.018066 -0.006599
                                                         -0.031264 -0.006599 0.018066 0.042731
                                                         -0.055929 0.005733 -0.018931 -0.006599]
                                                :sum 0.72538
                                                :min -0.139389
                                                :max 0.202138}
                       "blk.41.ffn_up.weight" {:block 0
                                                :value-count 256
                                                :sample [0.034667 -0.011521 0.006954 0.016192
                                                         0.006954 0.025429 0.043905 -0.002283
                                                         0.006954 0.006954 0.006954 -0.020758
                                                         0.006954 0.053142 0.006954 -0.011521]
                                                :sum 0.350408
                                                :min -0.076183
                                                :max 0.056068}}
   :gguf/q6-k-samples {"blk.0.attn_v.weight" {:block 0
                                               :value-count 256
                                               :sample [-0.006845 -0.01198 -0.022248 0.047918
                                                        -0.003423 0.010268 -0.027382 0.054764
                                                        0.034227 -0.01198 -0.006845 0.015402
                                                        0.008557 0.030805 -0.032516 -0.032516]
                                               :sum -0.043894
                                               :min -0.094727
                                               :max 0.088806}
                       "blk.0.ffn_down.weight" {:block 0
                                                :value-count 256
                                                :sample [0.004841 0.013312 -0.009682 -0.026625
                                                         -0.00242 0.0 -0.003631 0.037517
                                                         -0.003631 -0.026625 0.038727 0.00242
                                                         0.00121 0.020574 0.022994 0.012102]
                                                :sum 0.212123
                                                :min -0.068848
                                                :max 0.053249}
                       "token_embd.weight" {:block 0
                                            :value-count 256
                                            :sample [-0.034324 0.005536 0.029895 -0.007751
                                                     0.007751 -0.022144 0.006643 0.01993
                                                     0.028788 0.022144 0.011072 -0.006643
                                                     0.014394 0.004429 -0.002214 0.028788]
                                            :sum -0.213976
                                            :min -0.051086
                                            :max 0.050674}}
   :gguf/token-embedding-samples {2 {:token-id 2
                                     :value-count 2560
                                     :sample [-0.028427 0.055078 0.030204 0.033757
                                              -0.044417 -0.031981 0.023097 0.024874
                                              -0.014214 0.049747 0.014214 -0.046194
                                              -0.017767 -0.012437 0.035534 -0.003553]
                                     :sum -3.432013
                                     :min -0.098152
                                     :max 0.084961}}
   :gguf/rmsnorm-samples {:blk.0/attn_norm {:value-count 2560
                                            :sample [-9.542505 19.018743 13.227827 12.103419
                                                     -14.910164 -10.427495 7.753285 8.978163
                                                     -4.429227 15.981131 4.617341 -14.95078
                                                     -5.643417 -5.506607 14.450568 -1.175712]
                                            :sum -1463.854214
                                            :min -147.408141
                                            :max 113.803208}
                         :blk.0/ffn_norm {:value-count 2560
                                           :sample [-8.311214 15.904175 2.239464 2.18816
                                                    -11.757119 -9.157727 6.085912 4.997844
                                                    -4.035898 4.548936 4.070101 -3.042956
                                                    -2.330047 -3.785792 12.056391 -0.163798]
                                           :sum -972.988346
                                           :min -45.702691
                                           :max 39.935647}
                         :output/norm {:value-count 2560
                                       :sample [-9.74772 19.615149 5.95979 11.128647
                                                -16.994381 -12.082043 7.642127 9.397144
                                                -4.925164 9.217581 4.890961 -16.896048
                                                -6.284714 -4.279591 12.654935 -1.274044]
                                       :sum -1184.465932
                                       :min -36.845099
                                       :max 29.337727}}
   :gguf/projection-samples {:blk.0/attn_q {:rows [0 1 2 3]
                                            :value-count 4
                                            :sample [-45.52616 -9.02556 17.067708 10.863354]
                                            :sum -26.620658
                                            :min -45.52616
                                            :max 17.067708}
                             :blk.0/attn_k {:rows [0 1 2 3]
                                            :value-count 4
                                            :sample [2.762013 5.861241 1.781327 -12.989838]
                                            :sum -2.585257
                                            :min -12.989838
                                            :max 5.861241}
                             :blk.0/attn_v {:rows [0 1 2 3]
                                            :value-count 4
                                            :sample [12.377182 6.985937 -25.919527 -9.185445]
                                            :sum -15.741853
                                            :min -25.919527
                                            :max 12.377182}}
   :gguf/rope-samples {:blk.0/attn_q {:position 1
                                       :rope-dim 512
                                       :theta 1000000.0
                                       :value-count 4
                                       :sample [-17.003142 -43.185473 1.142816 20.199334]
                                       :sum -38.846467
                                       :min -43.185473
                                       :max 20.199334}
                       :blk.0/attn_k {:position 1
                                       :rope-dim 512
                                       :theta 1000000.0
                                       :value-count 4
                                       :sample [-3.439742 5.490996 11.586776 -6.136419]
                                       :sum 7.501611
                                       :min -6.136419
                                       :max 11.586776}}
   :gguf/head-samples {:blk.0/attn_q {:head 0
                                       :position 1
                                       :rows [0 255]
                                       :rope? true
                                       :value-count 256
                                       :sample [-17.003142 -43.185473 1.142816 20.199334
                                                8.733221 -1.21323 25.764199 -6.928831
                                                12.818862 -6.840196 9.592058 3.057451
                                                10.090412 31.378267 -39.082596 -10.293496]
                                       :sum 507.18331
                                       :min -47.277438
                                       :max 46.409391}
                       :blk.0/attn_k {:kv-head 0
                                       :position 1
                                       :rows [0 255]
                                       :rope? true
                                       :value-count 256
                                       :sample [-3.439742 5.490996 11.586776 -6.136419
                                                -5.520219 -10.766966 -11.001778 -10.644984
                                                -14.205547 -9.792202 14.644115 36.676927
                                                -20.806993 14.045396 -1.463094 7.666998]
                                       :sum -234.295058
                                       :min -125.756052
                                       :max 105.184306}
                       :blk.0/attn_v {:kv-head 0
                                       :rows [0 255]
                                       :rope? false
                                       :value-count 256
                                       :sample [12.377182 6.985937 -25.919527 -9.185445
                                                6.495511 9.778855 16.428084 34.7449
                                                -2.633967 -33.338899 -20.009645 -7.045471
                                                -13.946964 -18.699881 -28.990102 -7.560946]
                                       :sum 618.697992
                                       :min -105.957329
                                       :max 524.540105}}
   :gguf/attention-samples {:blk.0/head0-token0 {:q-head 0
                                                  :kv-head 0
                                                  :position 1
                                                  :head-dim 256
                                                  :weights [1.0]
                                                  :value-count 256
                                                  :sample [12.377182 6.985937 -25.919527 -9.185445
                                                           6.495511 9.778855 16.428084 34.7449
                                                           -2.633967 -33.338899 -20.009645 -7.045471
                                                           -13.946964 -18.699881 -28.990102 -7.560946]
                                                  :sum 618.697992
                                                  :min -105.957329
                                                  :max 524.540105
                                                  :score 858.776549}
                            :blk.0/all-heads-token0 {:q-head-range [0 7]
                                                      :kv-head-range [0 1]
                                                      :position 1
                                                      :head-dim 256
                                                      :weights [1.0]
                                                      :kv-heads [0 0 0 0 1 1 1 1]
                                                      :value-count 2048
                                                      :sample [12.377182 6.985937 -25.919527 -9.185445
                                                               6.495511 9.778855 16.428084 34.7449
                                                               -2.633967 -33.338899 -20.009645 -7.045471
                                                               -13.946964 -18.699881 -28.990102 -7.560946]
                                                      :sum 1308.781106
                                                      :min -105.957329
                                                      :max 524.540105
                                                      :score-sample [858.776549 300.269659 3052.038987 447.712963
                                                                     3938.141916 3307.641934 1107.97142 2448.466657]
                                                      :score-sum 15461.020084
                                                      :score-min 300.269659
                                                      :score-max 3938.141916}
                            :blk.0/all-heads-token1-causal {:token-ids [2 1]
                                                            :query-token-index 1
                                                            :position 2
                                                            :visible-token-count 2
                                                            :q-head-range [0 7]
                                                            :kv-head-range [0 1]
                                                            :head-dim 256
                                                            :kv-heads [0 0 0 0 1 1 1 1]
                                                            :value-count 2048
                                                            :sample [12.377182 6.985937 -25.919527 -9.185445
                                                                     6.495511 9.778855 16.428084 34.7449
                                                                     -2.633967 -33.338899 -20.009645 -7.045471
                                                                     -13.946964 -18.699881 -28.990102 -7.560946]
                                                            :sum 1715.92625
                                                            :min -105.957329
                                                            :max 524.540105
                                                            :score-sample [[3455.861546 3313.913438]
                                                                           [2602.339283 2360.228822]
                                                                           [2557.991626 3072.474513]
                                                                           [2189.928838 2384.24292]
                                                                           [6915.793803 8040.400481]
                                                                           [6594.745858 7642.837974]
                                                                           [7306.160567 8377.675052]
                                                                           [5790.5647 8021.439567]]
                                                            :weight-sample [[1.0 0.0]
                                                                            [1.0 0.0]
                                                                            [0.0 1.0]
                                                                            [0.0 1.0]
                                                                            [0.0 1.0]
                                                                            [0.0 1.0]
                                                                            [0.0 1.0]
                                                                            [0.0 1.0]]
                                                            :score-sum 80626.598989
                                                            :score-min 2189.928838
                                                            :score-max 8377.675052}}
   :gguf/attention-output-samples {:blk.0/attn_output {:rows [0 1 2 3]
                                                        :value-count 4
                                                        :sample [-29.722004 -23.17544 16.067558 14.977672]
                                                        :sum -21.852214
                                                        :min -29.722004
                                                        :max 16.067558}
                                   :blk.0/attn_output_full {:row-range [0 2559]
                                                            :row-count 2560
                                                            :value-count 2560
                                                            :sample [-29.722004 -23.17544 16.067558 14.977672
                                                                     4.141382 3.138767 3.956998 -16.81225
                                                                     -4.993406 3.892514 9.3593 3.76334
                                                                     13.311811 11.999482 0.219737 7.77573]
                                                            :sum 172.844843
                                                            :min -123.386853
                                                            :max 204.681356}
                                   :blk.0/attn_output_all_heads {:row-range [0 2559]
                                                                 :row-count 2560
                                                                 :value-count 2560
                                                                 :sample [-19.385777 -16.479798 12.116745 86.31488
                                                                          -35.930654 -42.579809 11.631068 7.986794
                                                                          8.451271 22.938344 20.931656 8.726331
                                                                          28.811372 -15.041754 -4.533569 -38.74554]
                                                                 :sum 674.633087
                                                                 :min -568.725398
                                                                 :max 954.891269}}
   :gguf/mlp-samples {:blk.0/ffn_gate {:rows [0 1 2 3]
                                        :value-count 4
                                        :sample [-8.309769 -10.082826 -10.458492 -12.436209]
                                        :sum -41.287297
                                        :min -12.436209
                                        :max -8.309769}
                      :blk.0/ffn_up {:rows [0 1 2 3]
                                      :value-count 4
                                      :sample [3.164884 -2.190473 -27.537988 12.701362]
                                      :sum -13.862215
                                      :min -27.537988
                                      :max 12.701362}
                      :blk.0/ffn_activation {:rows [0 1 2 3]
                                             :value-count 4
                                             :sample [-0.006471 9.23E-4 0.008267 -6.27E-4]
                                             :sum 0.002091
                                             :min -0.006471
                                             :max 0.008267}
                      :blk.0/ffn_down {:rows [0 1 2 3]
                                       :value-count 4
                                       :sample [-8.2E-5 2.98E-4 2.93E-4 -1.4E-4]
                                       :sum 3.68E-4
                                       :min -1.4E-4
                                       :max 2.98E-4}
                      :blk.0/ffn_gate_full {:row-range [0 10239]
                                             :row-count 10240
                                             :value-count 10240
                                             :sample [-8.309769 -10.082826 -10.458492 -12.436209
                                                      6.526065 -4.987348 -5.093058 -8.328873
                                                      1.228039 -21.997309 -12.25703 -22.912539
                                                      -7.552858 6.428225 0.854976 -2.12139]
                                             :sum -61803.077322
                                             :min -48.908104
                                             :max 142.473424}
                      :blk.0/ffn_up_full {:row-range [0 10239]
                                           :row-count 10240
                                           :value-count 10240
                                           :sample [3.164884 -2.190473 -27.537988 12.701362
                                                    0.065879 -10.203591 2.003726 4.170693
                                                    0.884263 -9.882534 -3.69524 -14.573461
                                                    3.800095 -6.046821 3.393646 2.729366]
                                           :sum 159.154494
                                           :min -133.398441
                                           :max 79.237713}
                      :blk.0/ffn_activation_full {:row-range [0 10239]
                                                   :row-count 10240
                                                   :value-count 10240
                                                   :sample [-0.006471 9.23E-4 0.008267 -6.27E-4
                                                            0.429302 0.344899 -0.062269 -0.008385
                                                            0.839924 0.0 2.15E-4 0.0
                                                            -0.015049 -38.807641 2.035711 -0.619739]
                                                   :sum -13226.405841
                                                   :min -19005.732668
                                                   :max 3113.508802}
                      :blk.0/ffn_down_full {:row-range [0 2559]
                                             :row-count 2560
                                             :value-count 2560
                                             :sample [-325.914054 5.66649 745.384302 301.489546
                                                      34.901631 374.543236 -57.261796 -369.121555
                                                      -638.696384 145.609252 1016.571174 982.759883
                                                      -120.902103 -1426.5082 310.331744 665.175388]
                                             :sum -16264.364623
                                             :min -1952.473832
                                             :max 2437.597269}}
   :gguf/block-samples {:blk.0/block0_full
                        {:attention-residual {:token-id 2
                                              :layer 0
                                              :stage :attention-residual
                                              :value-count 2560
                                              :sample [-19.414204 -16.42472 12.146949 86.348638
                                                       -35.975072 -42.61179 11.654165 8.011668
                                                       8.437058 22.988091 20.945869 8.680137
                                                       28.793605 -15.05419 -4.498035 -38.749093]
                                              :sum 671.201074
                                              :min -568.730695
                                              :max 954.887252}
                         :ffn-norm {:token-id 2
                                    :layer 0
                                    :stage :ffn-norm
                                    :value-count 2560
                                    :sample [-3.059 -2.556012 0.485375 3.016452
                                             -5.131898 -6.575979 1.654927 0.867547
                                             1.291091 1.132844 3.232429 0.308152
                                             2.035055 -2.469628 -0.822481 -0.962622]
                                    :sum 34.203266
                                    :min -50.65711
                                    :max 37.840531}
                         :ffn-gate {:row-range [0 10239]
                                    :row-count 10240
                                    :value-count 10240
                                    :sample [18.816664 16.290039 5.635495 13.897235
                                             -2.336039 8.163234 3.060494 25.562608
                                             21.196809 7.137104 7.20272 7.408276
                                             10.4354 -2.57675 23.062139 26.473512]
                                    :sum 108682.957264
                                    :min -59.996484
                                    :max 108.520902}
                         :ffn-up {:row-range [0 10239]
                                  :row-count 10240
                                  :value-count 10240
                                  :sample [-7.648589 -9.638476 -8.870587 -5.44292
                                           -8.736997 3.14202 4.941114 3.091855
                                           18.18191 -8.090306 -8.367592 0.667386
                                           15.132951 8.338428 -0.290179 -3.63402]
                                  :sum 708.89502
                                  :min -65.178748
                                  :max 73.11796}
                         :ffn-activation {:token-id 2
                                          :layer 0
                                          :stage :ffn-activation
                                          :row-range [0 10239]
                                          :row-count 10240
                                          :value-count 10240
                                          :sample [-143.920918 -157.011138 -49.812372 -75.641467
                                                   1.799789 25.641738 14.445278 79.035883
                                                   385.398458 -57.695488 -60.224582 4.941184
                                                   157.913761 -1.517985 -6.692141 -96.205279]
                                          :sum -18017.489491
                                          :min -2658.20311
                                          :max 5060.873045}
                         :ffn-down {:row-range [0 2559]
                                    :row-count 2560
                                    :value-count 2560
                                    :sample [-254.30449 -620.215068 -75.662264 1552.710015
                                             -269.702807 -113.152334 58.228364 444.859958
                                             -394.397343 -228.683596 -731.369236 -238.4796
                                             186.33189 863.437798 681.34778 -735.78125]
                                    :sum 13105.309257
                                    :min -6685.617136
                                    :max 2932.052636}
                         :block-output {:token-id 2
                                        :layer 0
                                        :stage :block-output
                                        :value-count 2560
                                        :sample [-273.718694 -636.639788 -63.515315 1639.058653
                                                 -305.677878 -155.764124 69.882529 452.871625
                                                 -385.960285 -205.695505 -710.423367 -229.799463
                                                 215.125495 848.383608 676.849745 -774.530343]
                                        :sum 13776.510331
                                        :min -6669.549869
                                        :max 2836.424105}
                         :output-norm {:token-id 2
                                       :layer 0
                                       :stage :output-norm-after-block
                                       :value-count 2560
                                       :sample [-4.549223 -10.989385 -0.60745 26.189846
                                                -5.668642 -2.852233 1.1207 8.292637
                                                -6.482209 -1.847283 -11.848713 -4.073901
                                                3.688307 14.149666 11.683471 -13.459924]
                                       :sum 225.494419
                                       :min -107.347886
                                       :max 47.686982}
                         :logits {:token-ids [1 2 3 4]
                                  :value-count 4
                                  :sample [-21.50351 12.06829 15.421071 17.125854]
                                  :sum 23.111705
                                  :min -21.50351
                                  :max 17.125854
                                  :greedy-token-id 4
                                  :greedy-logit 17.125854}}}
   :gguf/multi-layer-block-samples
   {:blk.1/block1_full
    {:attention {:layer 1
                 :position 1
                 :q-head-range [0 7]
                 :kv-head-range [0 1]
                 :head-dim 256
                 :weights [1.0]
                 :kv-heads [0 0 0 0 1 1 1 1]
                 :value-count 2048
                 :sample [21.786053 -13.170049 -0.048589 -0.096977
                          26.311274 12.255456 49.101642 -20.358861
                          50.512567 -17.410559 20.246101 18.116765
                          28.716184 -22.780965 17.584852 44.937288]
                 :sum 788.907301
                 :min -61.302007
                 :max 80.281081
                 :score-sample [830.031093 -1191.730593 -2018.284975 -108.869992
                                1140.300444 -258.138355 -600.771842 2787.962757]
                 :score-sum 580.498537
                 :score-min -2018.284975
                 :score-max 2787.962757}
     :attention-output {:row-range [0 2559]
                        :row-count 2560
                        :value-count 2560
                        :sample [39.83607 -21.635531 -57.168733 3.460494
                                 4.597001 37.6744 15.010376 22.324026
                                 38.00499 26.270887 16.398224 -3.4293
                                 9.239983 19.781714 -17.422046 72.606097]
                        :sum -1086.196063
                        :min -157.882735
                        :max 93.661843}
     :attention-residual {:token-id 2
                          :layer 1
                          :stage :attention-residual
                          :value-count 2560
                          :sample [-233.882624 -658.275319 -120.684049 1642.519147
                                   -301.080878 -118.089725 84.892905 475.195651
                                   -347.955295 -179.424618 -694.025143 -233.228762
                                   224.365478 868.165322 659.427699 -701.924246]
                          :sum 12690.314268
                          :min -6652.598696
                          :max 2686.403964}
     :ffn-norm {:token-id 2
                :layer 1
                :stage :ffn-norm
                :value-count 2560
                :sample [-22.012238 -46.312625 -0.952388 8.083362
                         -11.573161 -5.41955 2.610539 20.590661
                         -16.131004 -2.257151 -33.953096 -1.331169
                         3.358258 13.854086 65.750012 -1.079498]
                :sum 446.019297
                :min -608.581749
                :max 275.961515}
     :ffn-gate {:row-range [0 10239]
                :row-count 10240
                :value-count 10240
                :sample [60.300053 -14.162969 -22.470365 -64.925036
                         37.703094 -35.016705 -5.746486 -103.322089
                         49.979813 23.45504 48.105312 -59.484881
                         -167.374841 -52.290491 -57.731751 -11.661877]
                :sum -57332.133677
                :min -260.177375
                :max 252.398786}
     :ffn-up {:row-range [0 10239]
              :row-count 10240
              :value-count 10240
              :sample [144.715679 -65.931181 -61.169855 15.516139
                       -69.298586 -4.881075 12.452117 -104.668789
                       1.452023 -79.787996 -40.19423 0.286647
                       -145.678517 47.81996 6.850005 -44.714787]
              :sum -673.365792
              :min -263.011533
              :max 245.561017}
     :ffn-activation {:token-id 2
                      :layer 1
                      :stage :ffn-activation
                      :row-range [0 10239]
                      :row-count 10240
                      :value-count 10240
                      :sample [8726.363206 6.6E-4 0.0 0.0
                               -2612.771063 0.0 -0.227821 0.0
                               72.571817 -1871.43066 -1933.555977 0.0
                               0.0 0.0 0.0 0.004493]
                      :sum 253915.766141
                      :min -27442.064287
                      :max 33378.218086}
     :ffn-down {:row-range [0 2559]
                :row-count 2560
                :value-count 2560
                :sample [1864.634489 -438.927611 9543.688334 7276.804708
                         -5359.877308 5230.280523 -1268.196569 2538.125348
                         6623.074802 -3988.847211 3550.41445 -524.770089
                         5131.32195 -493.110328 -6004.127822 -8707.934157]
                :sum -19857.682123
                :min -25774.566826
                :max 31497.618416}
     :block-output {:token-id 2
                    :layer 1
                    :stage :block-output
                    :value-count 2560
                    :sample [1630.751866 -1097.20293 9423.004285 8919.323854
                             -5660.958186 5112.190798 -1183.303664 3013.320999
                             6275.119507 -4168.271828 2856.389308 -757.998851
                             5355.687428 375.054994 -5344.700123 -9409.858404]
                    :sum -7167.367855
                    :min -25625.000259
                    :max 31774.676193}
     :output-norm {:token-id 2
                   :layer 1
                   :stage :output-norm-after-block
                   :value-count 2560
                   :sample [2.518763 -1.760083 8.375056 13.244543
                            -9.755995 8.699438 -1.763532 5.127782
                            9.7942 -3.478814 4.427289 -1.248811
                            8.5333 0.58132 -8.573725 -15.196859]
                   :sum -40.1649
                   :min -38.190123
                   :max 52.349097}
     :logits {:token-ids [1 2 3 4]
              :value-count 4
              :sample [-14.713764 2.465819 3.89823 12.240504]
              :sum 3.890789
              :min -14.713764
              :max 12.240504
              :greedy-token-id 4
              :greedy-logit 12.240504}}}
   :gguf/output-logit-samples {:token_embd/candidates {:token-ids [1 2 3 4]
                                                        :value-count 4
                                                        :sample [44.15111 579.290428 29.142786 19.768841]
                                                        :sum 672.353165
                                                        :min 19.768841
                                                        :max 579.290428
                                                        :greedy-token-id 2
                                                        :greedy-logit 579.290428}
                                :token_embd/full-vocab {:token-id-range [0 262143]
                                                        :value-count 262144
                                                        :sample [27.729826 44.15111 579.290428 29.142786
                                                                 19.768841 18.611226 11.031361 8.567112
                                                                 -0.634776 -6.818355 14.057829 24.598353
                                                                 4.391335 -1.881607 17.838161 -2.082648]
                                                        :sum 3231214.589737
                                                        :min -42.362666
                                                        :min-token-id 3104
                                                        :max 579.290428
                                                        :max-token-id 2
                                                        :greedy-token-id 2
                                                        :greedy-logit 579.290428
                                                        :top-k [{:token-id 2 :logit 579.290428}
                                                                {:token-id 93163 :logit 66.902076}
                                                                {:token-id 255271 :logit 65.799499}
                                                                {:token-id 230236 :logit 64.580503}
                                                                {:token-id 232009 :logit 62.16587}
                                                                {:token-id 222557 :logit 61.881457}
                                                                {:token-id 120047 :logit 61.448553}
                                                                {:token-id 115 :logit 61.233008}
                                                                {:token-id 233449 :logit 60.473853}
                                                                {:token-id 117709 :logit 59.999999}
                                                                {:token-id 162978 :logit 59.466545}
                                                                {:token-id 220153 :logit 59.278312}
                                                                {:token-id 112 :logit 59.062668}
                                                                {:token-id 215641 :logit 58.976559}
                                                                {:token-id 208478 :logit 58.327678}
                                                                {:token-id 111 :logit 58.239818}]}}})

(def required-direct-lowering-ops
  "Minimum ops a direct Gemma runtime must lower to num-clj or host-provided
  fused kernels. The existing num contract already covers dense matmul/gemv and
  elementwise/reductions; the remaining entries make the transformer-specific
  gap explicit and testable."
  [:tokenize
   :detokenize
   :gguf-metadata
   :gguf-tensor-read
   :q4-k-dequantize
   :q6-k-dequantize
   :token-embedding
   :rmsnorm
   :rope
   :grouped-query-attention
   :sliding-window-attention
   :kv-cache
   :gated-mlp
   :transformer-block-composition
   :logit-softcap
   :sample-greedy])

(defn gemma4-e4b-graph
  "Torch-clj model graph contract for Gemma 4 E4B text generation.

  The graph intentionally uses custom layer names for transformer-specific
  stages so hosts can lower them to num-clj ops or fused kernels while retaining
  an inspectable EDN model graph."
  ([] (gemma4-e4b-graph gemma4-e4b-expected))
  ([manifest]
   (let [vocab-size (:kotodama/vocab-size manifest 262144)
         hidden-size (:gemma4/embedding-length manifest)
         intermediate-size (:gemma4/feed-forward-length manifest)
         block-count (:gemma4/block-count manifest)]
     (apply torch/sequential
            (concat
             [(torch/embedding vocab-size hidden-size)]
             (mapv (fn [layer-index]
                     (torch/layer :gemma4-block
                                  {:layer-index layer-index
                                   :hidden-size hidden-size
                                   :intermediate-size intermediate-size
                                   :head-count (:gemma4/attention-head-count manifest)
                                   :head-count-kv (:gemma4/attention-head-count-kv manifest)
                                   :rope-dimension-count (:gemma4/rope-dimension-count manifest)}))
                   (range block-count))
             [(torch/layer :rmsnorm {:hidden-size hidden-size})
              (torch/linear hidden-size vocab-size)
              (torch/layer :logit-softcap {})])))))

(defn runtime-spec
  "Runtime spec for direct Gemma 4 E4B loading through torch-clj + num-clj."
  ([] (runtime-spec {}))
  ([opts]
   (let [direct {:kotodama/artifact-format :gguf
                 :kotodama/model-family :gemma4
                 :kotodama/direct-lowering-ops required-direct-lowering-ops}
         opts* (merge {:kotodama/backend :native
                       :kotodama/compute-backend :num/cpu
                       :kotodama/model-graph (gemma4-e4b-graph)}
                      opts)]
     (merge (rt/transformer (:kotodama/model opts* (:kotodama/model gemma4-e4b-expected))
                            opts*)
            direct
            (select-keys opts [:kotodama/artifact-path
                               :kotodama/artifact-sha256
                               :kotodama/direct-lowering-ops])))))

(defn direct-ready?
  "True when a runtime probe advertises every op required for direct Gemma
  generation. This lets hosts expose partial progress without pretending full
  Gemma decoding is complete."
  [probe]
  (let [ops (set (:kotodama/direct-lowering-ops probe))]
    (every? ops required-direct-lowering-ops)))
