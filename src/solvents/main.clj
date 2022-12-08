(ns solvents.main
  (:require [contajners.core :as c])
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

(def
  ^{}
  runner
  (fn []
    (let [client  (make-client)
          boxes   (:containers client)
          nets    (:networks client)
          
          exists  (fn [e k l] (-> (comp #{e} k)
                                  (filter l)
                                  (count)
                                  (> 0)))
          netname "acid"]

      (when-not (exists netname :Name (c/invoke nets {:op :NetworkList}))
        (c/invoke nets {:op :NetworkCreate
                        :data {:CheckDuplicate true
                               :Name netname}}))

      (when-not (exists ["/solutes-api"] :Names (c/invoke boxes {:op :ContainerList
                                                                 :params {:all true}}))
        (c/invoke boxes {:op :ContainerCreate
                         :params {:name "solutes-api"}
                         :data {:Cmd ["/bin/sh" "-c" "while true; do sleep 10; done"]
                                :Image "python:3.8-slim"
                                :HostConfig {:binds [(format "%s:/tmp/b" (System/getProperty "user.dir"))]}
                                :NetworkingConfig {:NetworkID "acid"}}
                         :throw-exception true})
        )

      (c/invoke boxes {:op :ContainerStart
                       :params {:id "solutes-api"}})

      )))


(comment

  (runner)
  )

(defn -main [& argv]
(let [exit-code (runner)]
  (prn argv)
  (prn {:exit-code exit-code})))
