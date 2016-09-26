(ns simplecl.test.debug
  (:require
   [simplecl.core :as cl]
   [simplecl.utils :as clu]
   [simplecl.ops :as ops]
   [thi.ng.structgen.core :as sg]
   [thi.ng.structgen.parser :as sp]))

(def debug-kernel
  "#define OFFSETOF(type, field) ((unsigned long) &(((type*) 0)->field))

typedef struct {
    float4   a;    // 0
    int      b[8]; // 16
    char     c[8]; // 48
    float3   d;    // 64
} Foo;

kernel void Debug(__global Foo* in,
                  __global Foo* out,
                  const unsigned int n,
                  const float deltaSq) {
	unsigned int id = get_global_id(0);
	if (id < n) {
		global Foo *p = &out[id];
		p->a = (float4)(OFFSETOF(Foo, b),
		                OFFSETOF(Foo, c),
		                OFFSETOF(Foo, d),
		                (sizeof *p));
	}
}")

(defn -main
  []
  (sg/reset-registry!)
  (sg/register! (sp/parse-specs (slurp (clu/str->stream debug-kernel))))
  (let [state (cl/init-state
               :device :cpu
               :program (clu/str->stream debug-kernel))
        bar   (sg/make-struct :Bar [:foo :Foo 4])
        pbuf  (sg/encode bar {})
        qbuf  (sg/encode bar {})]
    
    (cl/with-state state
      (println "build log:" (cl/build-log))
      (let [pclbuf (cl/as-clbuffer pbuf)
            qclbuf (cl/as-clbuffer qbuf :writeonly)
            n      (-> bar sg/struct-spec :foo sg/length)
            _      (println :n n :sizeof (sg/sizeof bar))
            pipe   (ops/compile-pipeline
                    :steps [{:name         "Debug"
                             :in           pclbuf
                             :out          qclbuf
                             :n            n
                             :write-buffer [:in :out]
                             :read-buffer  [:out]
                             :args         [[n :int] [0.5 :float]]}])]
        (->> (ops/execute-pipeline pipe :verbose true)
             (sg/decode bar)
             (println))))))
