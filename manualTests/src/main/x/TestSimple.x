module TestSimple {

    package json import json.xtclang.org;
    import json.*;

    void run() {
        JsonObject o = json.newObject([
        "status" = json.newObject([
            "hostIP" = "10.0.10.4",
            "hostIPs" = [
                ["ip" = "10.0.10.4"]
            ],
            "conditions" = [
                json.newObject([
                    "lastProbeTime" = Null,
                    "lastTransitionTime" = "2024-11-18T15:26:00Z",
                    "status" = False,
                    "type" = "PodReadyToStartContainers"
                    ]),
                json.newObject([
                    "lastProbeTime" = Null,
                    "lastTransitionTime" = "2024-11-18T15:26:00Z",
                    "message" = "containers with incomplete status: [coherence-k8s-utils]",
                    "reason" = "ContainersNotInitialized",
                    "status" = "False",
                    "type" = "Initialized"
                    ]),
                ],
            "containerStatuses" = [
                json.newObject([
                    "image" = "ghcr.io/thegridman/test:1.0.0",
                    "imageID" = "",
                    "lastState" = [],
                    "name" = "coherence",
                    "ready" = False,
                    "restartCount" = 0,
                    "started" = False,
                    "state" = [
                        "waiting" = ["reason" = "PodInitializing"],
                    ],
                ]),
            ],
        ]),
    ]);
    }
}