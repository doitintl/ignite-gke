# Apache Ignite optimized for GKE

Out take on running Apache Ignite in GKE properly.

The main differences to the official [guide](https://apacheignite.readme.io/docs/google-cloud-deployment):

* Switch to SSD PD since Apache Ignite quickly becomes IO bound
* WAL is kept on the same disk as the main data -
  provisioning dedicated disk for WAL is a waste since IO performance on GCE
  depends on a volume size and we do want WAL to reside on high-performance media
* GKE cluster is launched over two availability zones
* Stateful set is run as one pod per node, on a dedicated node-pool:
  * Ensures performance uniformity
  * Java is not necessary good at detecting container limits, hence running Java
    workloads as 1 container per host is still a very reasonable consideration,
	particularly for database workloads
* Stateful set launches pods in parallel to speed up the startup
* Localized config file through k8s configmap instead of pulling it from 3rd party GitHub repo
* Changed ignite k8s service to create *internal* L4 TCP Load Balancer instead of public L3
  load balancer as it is in the community guide (which opens you freshly created cluster to the world)
* Propagating k8s node's availability zone name into Ignite pod and providing a proper cache template
  in the node config.
* Switched Load Balancer session affinity to Round Robbin to ensure even client load distribution

## How to launch the setup
To create a new GKE cluster and launch Apache Ignite into it, use the following commands:

```
make PROJECT=<your project name> gke-create
make ignite-launch
```
Wait till pods are up and the run ``make ignite-activate`` - only required for the first
time after cluster creation.

If you change `ignite-config.xml` you can apply new config **and restart the cluster**
(the data is preserved) by running `make ignite rerun`.

### REST API quick access

* To activate REST API port forwarding on `localhost:8080` run `make por-forward`
* Then you can register on `console.gridgain.com` for free, download their agent and to run it
  locally to have a UI view on your cluster
* Also you can access your cluster's REST API on `localhost:8080`

## Zone-Awareness

Cluster K8s setup has zone-awareness pre-configured, including a proper cache-templated called
`zone-awareness-cache`. To test zone awareness allocation you can:

* Create a test cache from template by running `make create-cache CACHE=<your cache name`
* Check that partitions are properly distributed between zones (i.e. that there are no
  both primary and backup partition in the same node): `./check.py <your cache name>`
  (the tool requires Python 3.6+ available. No extra libraries are needed)
