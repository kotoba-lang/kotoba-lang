(ns kotoba.http
  "Assembled from one repo per definition.

  This namespace holds no implementation. It re-exports the definitions
  that each live in their own repo, so a call site can require one name
  and a library can require only the definitions it actually uses."
  (:require [kotoba.http.http :as ihttp-ns]
            [kotoba.http.decode-json-body :as decode-json-body-ns]
            [kotoba.http.fold-headers :as fold-headers-ns]
            [kotoba.http.header :as header-ns]
            [kotoba.http.mock-http :as mock-http-ns]
            [kotoba.http.parse-url :as parse-url-ns]
            [kotoba.http.request :as request-ns]
            [kotoba.http.request-json :as request-json-ns]
            [kotoba.http.response :as response-ns]
            [kotoba.http.set-header :as set-header-ns]))

(def decode-json-body "See kotoba.http.decode-json-body/decode-json-body." decode-json-body-ns/decode-json-body)
(def fold-headers "See kotoba.http.fold-headers/fold-headers." fold-headers-ns/fold-headers)
(def header "See kotoba.http.header/header." header-ns/header)
(def mock-http "See kotoba.http.mock-http/mock-http." mock-http-ns/mock-http)
(def parse-url "See kotoba.http.parse-url/parse-url." parse-url-ns/parse-url)
(def request "See kotoba.http.request/request." request-ns/request)
(def request-json "See kotoba.http.request-json/request-json." request-json-ns/request-json)
(def response "See kotoba.http.response/response." response-ns/response)
(def send "See kotoba.http.http/send." ihttp-ns/send)
(def set-header "See kotoba.http.set-header/set-header." set-header-ns/set-header)
