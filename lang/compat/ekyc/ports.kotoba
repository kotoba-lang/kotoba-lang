(ns ekyc.ports)

(defprotocol IEkyc
  (start-session! [port session])
  (submit-evidence! [port session evidence])
  (fetch-result [port session]))
