(ns solvents.main
  (:require [contajners.core :as c]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:gen-class))

(def
 ^{}
  make-client
  (fn []
    (let [conf {:engine :docker
                :version "v1.41"
                :conn {:uri "unix:///var/run/docker.sock"}}
          reconf (fn [m c] (c/client (assoc m :category c)))]
      (reduce
       (fn [a e]
         (assoc a e (reconf conf e)))
       {}
       [:networks
        :images
        :containers
        :volumes]))))

(defmacro contain [name data]
  `(do
     (~'gallery {:op :ImageCreate
                 :params {:fromImage (:Image ~data)}})
     
     (when-not (~'exists [(str "/" ~name)] :Names (~'box {:op :ContainerList
                                                          :params {:all true}}))
       (~'box {:op :ContainerCreate
               :params {:name ~name}
               :data ~data
               :throw-exceptions true}))
     (~'box {:op :ContainerStart
             :params {:id ~name}})))


(def
  ^{}
  runner
  (fn []
    (let [client  (make-client)
          box     (partial c/invoke (:containers client))
          net     (partial c/invoke (:networks client))
          gallery (partial c/invoke (:images client))
          
          exists  (fn [e k l] (-> (comp #{e} k)
                                  (filter l)
                                  (count)
                                  (> 0)))
          netname "acid"]

      (when-not (exists netname :Name (net {:op :NetworkList}))
        (net {:op :NetworkCreate
              :data {:CheckDuplicate true
                     :Name netname}}))

      (contain "acid-solutes"
               {:Image "acid/solutes"
                :HostConfig {:binds [(format "%s:/tmp/b" (System/getProperty "user.dir"))]}
                :NetworkingConfig {:NetworkID "acid"}
                :ExposedPorts {"8000/tcp" "8000/tcp"}
                })

      (contain "acid-acidoc"
               {:Image "swaggerapi/swagger-ui"
                :NetworkingConfig {:NetworkID "acid"}
                :Env [(format "API_URLS=%s" (json/write-str
                                             {:url "http://localhost/openapi.json"
                                              :name "test API doc"}))]})

      (map (fn [e] (:Names e)) (box {:op :ContainerList})))))


(comment

  (defmacro macro-eval [digit]
    `(* ~'left ~digit))

  (let [left 2]
    (macro-eval 5))

  (runner)
  )

(defn -main [& argv]
(let [exit-code (runner)]
  (prn argv)
  (prn {:exit-code exit-code})))
