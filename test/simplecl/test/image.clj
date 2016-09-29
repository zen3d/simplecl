(ns simplecl.test.image
  (:import
    [javax.imageio ImageIO]
    [java.awt.image BufferedImage RenderedImage WritableRaster DataBuffer]
    (java.io FileOutputStream))
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
                       int width,
                       int height)
  {
    int2 coord = (int2)(get_global_id(0), get_global_id(1));
    if (coord.x < width && coord.y < height) {
      uint4 temp = read_imageui(src, imageSampler, coord);
      write_imageui(dst, coord, (uint4)(temp.x ^ 255, temp.y ^ 255, temp.z ^ 255, temp.w ^ 255));
    }
  }
")

(defn ^BufferedImage load-image
  ([^String path]
   (with-open [in (clu/resource-stream path)]
     (ImageIO/read in))))

(defn make-image
  [width height]
  )

(defn save-image
  ([^String path ^BufferedImage image]
   (with-open [out (FileOutputStream. path)]
     (ImageIO/write image "PNG" out))))

(defn cvt-image
  [data width height]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
    (.setRGB image 0 0 width height data 0 (* 4 width))
    image)

  )

(defn dump-image
  [result path width height]
  (save-image path (cvt-image result width height))
  )

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
          (int-array)
          (dump-image "result.png" width height)
          )))))

(defn -main
  [& [device]]
  ;;(hello-cl :path "Mandril.tiff" :device :cpu)
  ;;(hello-cl :path "Mandril.tiff" :device :gpu)
  (image-cl :path "images/Mandrill.png" :device (keyword device)))

(-main)
