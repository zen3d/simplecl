(ns simplecl.test.image
  (:require
    [simplecl.core :as cl]
    [simplecl.utils :as clu]
    [simplecl.ops :as ops]))

(def image-kernel
  "__kernel void invert(__global const float* a,
                         __global const float* b,
                         __global float* c,
                         const uint n) {
    uint id = get_global_id(0);
    if (id < n) {
      c[id] = a[id] * b[id];
    }
}")

(defn verify
  [results num]
  (println
    "verified:"
    (= results (map #(float (* % %2)) (range num) (reverse (range num))))))

(defn image-cl
  [& {:keys [num device] :or {num 1024}}]
  (cl/with-state
    (cl/init-state :device device :program (clu/str->stream image-kernel))
    (println "using device:" (cl/device-name))
    (if-not (cl/build-ok?)
      (println "build log:\n----------\n" (cl/build-log))
      (let [num  (* 1024 num)
            data (range num)
            a    (cl/as-clbuffer :float data :readonly)
            b    (cl/as-clbuffer :float (reverse data) :readonly)
            c    (cl/make-buffer :float num :writeonly)]
        (-> (ops/compile-pipeline
              :steps [{:name "invert"
                       :in           [a b]
                       :out          c
                       :write-buffer [:in :out]
                       :read-buffer  [:out]
                       :args         [[num :int]]
                       :n            num}])
            (ops/execute-pipeline :verbose :true)
            (cl/buffer-seq)
            (verify num))))))

(defn -main
  [& [device]]
  ;;(hello-cl :num 1024 :device :cpu)
  ;;(hello-cl :num 1024 :device :gpu)
  (image-cl :num 1024 :device (keyword device)))
