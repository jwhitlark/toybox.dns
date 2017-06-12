(ns user
  (:require [toybox.clj.net.dns :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.java.shell :as shell]
            [clojure.set :as set]
            ;; Interactive tools
            [clojure.pprint :as pp]
            [gloss.core :as gc]
            [gloss.io :as gio :refer [encode decode]]
            [clojure.core.async
             :as csp
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout]]
            )
  (:import (java.net InetAddress DatagramPacket DatagramSocket))
  )

(defn ->hex [i] (Integer/toHexString i))



#_ (def socket (DatagramSocket. 8888))

#_ (receive-loop socket println)

#_ (def r (receive-chan socket))

#_ (def x (<!! r))

#_ (->> x pp-packet pp/pprint)

;; dig @127.0.0.1 -t a -p 8888  whitlark.org
;; don't want to do the following, will keep retrying
;; (shell/sh "dig" "@127.0.0.1" "-ta" "-p8888" "whitlark.org")

;; first two bytes vary, then 1 0 0 1 0 0 0 ...

#_ (57 123 ;; id
       1 0
       0 1
       0 0
       0 0
       0 0

       8 ;; length
       119 104 105 116 108 97 114 107 ;; whitlark
       3 ;; length
       111 114 103 ;; org
       0 ;; ?
       0 1 ;; type? (A)
       0 1) ;; class


;; gloss examples, from http://derek.troywest.com/articles/by-example-gloss/
;; Values are serialized in the order they are defined
;; (defcodec ordered-map-codec
;;   (ordered-map :zappa :byte
;;                :alpha :byte))

;; partial decode
;; (decode vector-codec (to-byte-buffer [126 127]))
;; => [126 127]


;; (decode vector-codec (to-byte-buffer [126 127 125]))
;; Exception Bytes left over after decoding frame.  gloss.io/decode (io.clj:86)


;; (decode vector-codec (to-byte-buffer [126 127 125]) false)
;; => [126 127]

;; also look at nested codecs
;; name groups are delimited by byte 0

;; not quite what I need but close
;; (repeated
;; (string :utf-8 :delimiters ["\r"])
;; :delimiters ["\n"]))


#_ (def b (byte-array 30))

;; mess with data before re-encoding it.

#_ (.get (gio/contiguous (encode dns (assoc-in (decode  dns (byte-array 30 (.getData y))) [0 :id] 10))) x)

#_ (->> y
     (.getData)
     (byte-array 30) ;; FIXME: copies and truncates data,  (jw 15-08- 8)
     (decode dns)
     (#(assoc-in % [0 :an-count] 1))
     (encode dns)
     (gio/contiguous)
     )
;; view the result with:
#_ (pp/pprint b)

(def lookup-table {[[119 104 105 116 108 97 114 107] [111 114 103]] [75 98 -95 -126]})

(defn receive-reply [socket packet]
  (let [origin-addr (.getAddress packet)
        origin-port (.getPort packet)
        req-size (.getLength packet)
        [header, question] (decode dns (byte-array req-size (.getData packet)))
        nm (get question :name)
        qty (get question :qtype)
        qcls (get question :qclass)
        ip (get lookup-table nm [0 0 0 0])

        new-header (-> header
                       (assoc-in [:opts :qr] true)
                       (assoc-in [:an-count] 1)
                       (assoc-in [:opts :ra] true))
        answer {:name nm :qtype qty :qclass qcls :ttl 1974 :rd-length 4 :rdata ip}

        msg (encode full-answer [new-header
                                 question
                                 answer])
        ]
    ;; do stuff with packet...
    (pp/pprint header)
    (pp/pprint new-header)
    (pp/pprint question)
    (pp/pprint answer)
    (let [cont (gio/contiguous msg)
          l (.capacity cont)
          a (byte-array l)
          _ (.get cont a)]
      (pp/pprint a)
      (send-packet socket a origin-addr origin-port)
      )


    )
  )


;;; Basic working.  Cleanup, add recursion?, writeup

#_ (receive-reply socket (<!! r))

(defn run [socket]
  (while  true
    (receive-reply socket (<!! r))))
