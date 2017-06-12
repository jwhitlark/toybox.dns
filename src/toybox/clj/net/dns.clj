(ns toybox.clj.net.dns
  (:require [clojure.pprint :as pp]
            [clojure.core.match :refer [match]]
            [gloss.core :as gc]
            [clojure.core.async :as csp
             :refer [>! <! >!! <!! go go-loop chan buffer close!
                     thread alts! alts!! timeout]])
  (:import (java.net InetAddress InetSocketAddress DatagramPacket DatagramSocket))
  )

;; (DatagramPacket. buffer? 512 inet-address int-port )

;; listen for udp requests
(defn receive
  "Block until a UDP message is received on the given DatagramSocket, and
  return the payload message as a string."
  [^DatagramSocket socket]
  (let [buffer (byte-array 512)
        packet (DatagramPacket. buffer 512)]
    (.receive socket packet)
    ;; (String. (.getData packet)
    ;;          0 (.getLength packet))
    ;;(.getData packet)
    packet
    ))

(defn send-packet
  [^DatagramSocket socket msg host port]
  (let [payload msg
        length (min (alength payload) 512)
        address (InetSocketAddress. host port)
        packet (DatagramPacket. payload length address)]
    (.send socket packet))
  )

(defn receive-loop
  "Given a function and DatagramSocket, will (in another thread) wait
  for the socket to receive a message, and whenever it does, will call
  the provided function on the incoming message."
  [socket f]
  (future (while true (f (receive socket)))))

#_ (def socket (DatagramSocket. 8888))

#_ (receive-loop socket println)

(defn receive-chan
  [socket]
  (let [c (chan)] ;; should this be buffered?
    (go
      (while (not (.isClosed socket))
        (->> (receive socket)
            (>! c))))
    c))

(def header-size 12) ;; in bytes

(defn bytes->str [b]
  (->> b (map char) (apply str)))

(defn names
  [qname]
  (loop [unparsed qname accum []]
    (if (empty? unparsed)
      accum
      (let [sz (first unparsed)
            nm (->> unparsed (drop 1) (take sz))
            rem (drop (inc sz) unparsed)]
        (recur rem (conj accum (bytes->str nm)) )
        ))))


(defn int8 [a]
  (bit-and 0xff a))

(defn int16 [a b]
  (-> a int8 (bit-shift-left 8) (bit-or (int8 b))))

;; (defn get-packet [packet]
;;   (let [p (.getData packet)
;;         sz (.getLength packet)
;;         data (take sz p)
;;         header (take header-size data)
;;         question (drop header-size data)
;;         qname (drop-last 5 question)
;;         qtype (apply int16 (take-last 2 (drop-last 2 question)))
;;         qclass (apply int16 (take-last 2 question))
;;         ]
;;     {:header header
;;      :question question
;;      :qname qname
;;      :qtype qtype
;;      :qclass qclass
;;      :source-addr (.getAddress packet)
;;      :source-port (.getPort packet)
;;      :names (names qname)
;;      })
;;   )

(defn pp-packet
  [packet]
  (let [p (.getData packet)
        sz (.getLength packet)
        data (take sz p)
        header (take header-size data)
        question (drop header-size data)
        qname (drop-last 5 question)
        qtype (apply int16 (take-last 2 (drop-last 2 question)))
        qclass (apply int16 (take-last 2 question))
        ]
    {:header header
     :question question
     :qname qname
     :qtype qtype
     :qclass qclass
     :source-addr (.getAddress packet)
     :source-port (.getPort packet)
     :names (names qname)
     }))

;; check type
;; check other options

(gc/defcodec dns-header
  (gc/ordered-map :id :int16

                  ;; could use compile-frame to add transforms for opcode, etc.
                  :opts (gc/bit-map :qr 1, :opcode 4, :aa 1, :tc 1,
                                    :rd 1, :ra 1, :z 3, :rcode 4)
                  :qd-count :int16
                  :an-count :int16
                  :ns-count :int16
                  :ar-count :int16))

;; QNAME
;; a domain name represented as a sequence of labels, where
;; each label consists of a length octet followed by that
;; number of octets.  The domain name terminates with the
;; zero length octet for the null label of the root.  Note
;; that this field may be an odd number of octets; no
;; padding is used.

;; more types at https://en.wikipedia.org/wiki/List_of_DNS_record_types
(def qtypes {:a 1
             :srv 33})

;; more classes at http://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml
(def qclasses {:internet 1})

(gc/defcodec dns-question
  (gc/ordered-map :name (gc/repeated (gc/repeated :byte
                                                  :prefix :byte)
                                     :delimiters [0])
                  :qtype (gc/enum :int16 qtypes)
                  :qclass (gc/enum :int16 qclasses)))

(gc/defcodec dns-answer
  (gc/ordered-map :name  (gc/repeated (gc/repeated :byte
                                                   :prefix :byte)
                                      :delimiters [0])
                  :qtype (gc/enum :int16 qtypes)
                  :qclass (gc/enum :int16 qclasses)
                  :ttl :int32
                  :rd-length :int16 ;; FIXME: hardcoded (jw 15-08- 8)
                  :rdata [:byte :byte :byte :byte]
                  ))

;; apply merge to result?
(gc/defcodec dns
  [dns-header dns-question]
  )


(gc/defcodec full-answer [dns-header dns-question dns-answer])
;; following seems to work, but I don't think multiple dns querys in a
;; single msg are frequently used.
;; (gc/defcodec dns-request
;;   (gc/header
;;    dns-header
;;    #(gc/compile-frame (concat [%] (repeat (:qd-count %) dns-question)))
;;    #(if (vector? %) (count %) 1)))

;; 75.98.161.130 -> [75, 98, -95, -126] -> [4b 62 a1 82]

;; compression is an offset from the str



;; First working response!!
;; dig @127.0.0.1 -t a -p 8888  whitlark.org

;;                                         ; <<>> DiG 9.8.3-P1 <<>> @127.0.0.1 -t a -p 8888 whitlark.org
;;                                         ; (1 server found)
;; ;; global options: +cmd
;; ;; Got answer:
;; ;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 4998
;; ;; flags: qr rd; QUERY: 1, ANSWER: 1, AUTHORITY: 0, ADDITIONAL: 0
;; ;; WARNING: recursion requested but not available

;; ;; QUESTION SECTION:
;;                                         ;whitlark.org.                  IN      A

;; ;; ANSWER SECTION:
;; whitlark.org.           1974    IN      A       75.98.161.130

;; ;; Query time: 16 msec
;; ;; SERVER: 127.0.0.1#8888(127.0.0.1)
;; ;; WHEN: Mon Aug 10 17:54:04 2015
;; ;; MSG SIZE  rcvd: 58
