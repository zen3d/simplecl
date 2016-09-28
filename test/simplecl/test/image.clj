(ns simplecl.test.image
  (:import
    [javax.imageio ImageIO]
    [java.awt.image BufferedImage RenderedImage WritableRaster DataBuffer]
    )
  (:require
    [simplecl.core :as cl]
    [simplecl.utils :as clu]
    [simplecl.ops :as ops]
    [clojure.java.io :as io])
  )

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

(defn ^BufferedImage load-image
  ([^String path]
   (with-open [in (clu/resource-stream path)]
     (ImageIO/read in))))

(defn image-cl
  [& {:keys [path device] :or {path "images/Mandrill.png"}}]
  (cl/with-state
    (cl/init-state :device device :program (clu/str->stream image-kernel))
    (println "using device:" (cl/device-name))
    (if-not (cl/build-ok?)
      (println "build log:\n----------\n" (cl/build-log))
      (let [img (load-image path)
            width (.getWidth img)
            height (.getHeight img)
            a    (cl/into-image img)
            b    (cl/into-image img)
            c    (cl/make-image width height)
            ]
        (-> (ops/compile-pipeline
              :steps [{:name "invert"
                       :in          [a b]
                       :out         c
                       :write-image [:in :out]
                       :read-image  [:out]
                       :args        [[num :int]]
                       :n           num}])
            (ops/execute-pipeline :verbose :true)
            (cl/buffer-seq)
            (verify num))))))

(defn -main
  [& [device]]
  ;;(hello-cl :path "Mandril.tiff" :device :cpu)
  ;;(hello-cl :path "Mandril.tiff" :device :gpu)
  (image-cl :path "images/Mandrill.png" :device (keyword device)))

(-main)
