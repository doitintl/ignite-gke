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
Wait till pods are up (run ``kubectl get pods`` and confirm the status of each pod is "Running").

Then run ``make ignite-activate`` - only required for the first
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


## Test Client
To build the test client:
* Clone this git repo
* Navigate to the [IgniteClient](IgniteClient) directory
* Build the local Dockerfile and give it an appropriate tag. E.g. ``docker build -t ignite_test .``

On build success, to run the test client:
* To view all command line options, run without arguments: ``docker run ignite_test``:
```
Options category 'misc':
  --[no]get (a boolean; default: "false")
    Perform GET requests. true/false
  --[no]help [-h] (a boolean; default: "false")
    Prints usage info.
  --[no]put (a boolean; default: "false")
    Perform PUT requests. true/false
Options category 'server settings':
  --host [-o] (a string; default: "")
    The Ignite server host.
  --name [-n] (a string; default: "test-cache")
    The name of the cache.
  --port [-p] (an integer; default: "10800")
    The server port.
Options category 'test setup':
  --count [-c] (an integer; default: "100")
    The count of objects to be processed.
  --lowerbound [-l] (an integer; default: "1")
    The lower bound of the integer key range.
  --sockets [-s] (an integer; default: "3")
    Parallel socket count.
  --upperbound [-u] (an integer; default: "-1")
    The upper bound of the integer key range. Must be set for random key 
    selection. If less than lower bound or left blank, upper bound will be 
    lower bound+count-1 and each key will be selected in that range will be 
    selected exactly once.
```
* The only required argument is the ``--host`` option. E.g. if the load balancer listens on IP ``10.128.0.21`` then the command ``docker run ignite_test --host 10.128.0.21`` can be used to establish connectivity (3 connections by default, this can be adjusted using the ``--sockets`` option). This will not try to insert or retrieve any data from the cluster. To put or get items the ``--put`` and/or ``--get`` options need to be specified.
* This command inserts, then tries to retrieve 100,000 objects with random keys between 1 and 100,000,000:  ``docker run ignite_test --get --put --host 10.128.0.21 --count 100000 --upperbound 100000000``
