(ns simplecl.test.image
  (:require
    [simplecl.core :as cl]
    [simplecl.utils :as clu]
    [simplecl.ops :as ops]
    [clojure.java.io :as io])
  )

(def image-kernel
  "
  constant sampler_t imageSampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP | CLK_FILTER_NEAREST;
  __kernel void invert(read_only image2d_t src,
                       write_only image2d_t dst,
                       const int width,
                       const int height)
  {
    int2 coord = (int2)(get_global_id(0), get_global_id(1));
    if (coord.x < width && coord.y < height) {
      uint4 temp = read_imageui(src, imageSampler, coord); // xyzw == bgra
      write_imageui(dst, coord, (uint4)(255 - temp.x, 255 - temp.y, 255 - temp.z, temp.w));
    }
  }
  "
  )

(defn dump-image
  [image path]
  (clu/save-image path image)
  )

(defn image-cl
  [& {:keys [path device] :or {path "images/Mandrill.png"}}]
  (cl/with-state
    (cl/init-state :device device :program (clu/str->stream image-kernel))
    (println "using device:" (cl/device-name))
    (if-not (cl/build-ok?)
      (println "build log:\n----------\n" (cl/build-log))
      (let [img (clu/load-image path)
            width (.getWidth img)
            height (.getHeight img)
            src    (cl/into-climage img)
            dst    (cl/make-climage width height)                                         ;(cl/make-climage width height)
            ]
        (->
          (ops/compile-pipeline
              :steps [{:name "invert"
                       :in          [src]
                       :out         dst
                       :write-image [:in :out]
                       :read-image  [:out]
                       :args        [[width :int] [height :int]]
                       :n (max width height)}])
          (ops/execute-pipeline :verbose :true)
          (cl/buffer-seq)
          (cl/image-from-kernel-output width height)
          (dump-image "result.png")
          )))))

(defn -main
  [& [device]]
  ;;(image-cl :path "images/Mandrill.png" :device :cpu)
  ;;(image-cl :path "images/Mandrill.png" :device :gpu)
  (image-cl :path "images/Mandrill.png" :device (keyword device))
  )

;;(-main)
