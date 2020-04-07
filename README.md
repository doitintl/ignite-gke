# Apache Ignite optimized for GKE

Out take on running Apache Ignite in GKE properly.

Main difference compared to the official [guide](https://apacheignite.readme.io/docs/google-cloud-deployment):

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
  load balancer it is in the community guide (which opens you freshly created cluster to the world)
* Propagating k8s node's availability zone name into Ignite pod in attempt to configure
  zone-aware primary/backup partition distribution. Still WIP on the ignite side
  ([SO question](https://stackoverflow.com/questions/61062929/apache-ignite-zonerack-aware-parititons/61064478#61064478))

## How to launch the setup
The create a new GKE cluster and launch Apache Ignite into it, use the following commands:

```
make PROJECT=<your project name> gke-create
make ignite-launch
```
