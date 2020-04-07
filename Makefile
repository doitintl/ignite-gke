PROJECT = zaar-playground
REGION = us-central1
ZONES = "us-central1-a,us-central1-c"

GKE_VERSION = 1.14.10-gke.27
GKE_NAME = ignite
INSTANCE_TYPE = n2-highmem-2
IGNITE_STORAGE_NODES = 2

gke-storage-pool-create:
	gcloud container node-pools create ignite-nodes --cluster=$(GKE_NAME) \
		--region=$(REGION) \
		--num-nodes=$(IGNITE_STORAGE_NODES) --machine-type=$(INSTANCE_TYPE) \
		--image-type=COS --node-labels=os=cos,workload=ignite-node \
		--node-locations $(ZONES)

gke-create:
	gcloud beta container clusters create $(GKE_NAME) \
		--num-nodes=1 \
		--region=$(REGION) \
		--cluster-version=$(GKE_VERSION) \
		--enable-stackdriver-kubernetes --enable-ip-alias \
		--enable-autoupgrade --enable-autorepair \
		--node-locations $(ZONES)
	gcloud container clusters get-credentials --region=$(REGION) $(GKE_NAME)

	$(MAKE) gke-storage-pool-create

	gcloud container node-pools delete default-pool --region=$(REGION) --quiet --cluster=$(GKE_NAME)

gke-delete:
	gcloud container clusters delete --quiet $(GKE_NAME) ||:

ignite-config:
	kubectl create configmap --namespace=ignite ignite-config \
			--from-file ignite-config.xml -o yaml --dry-run | kubectl apply -f -

ignite-launch: ignite-config
	kubectl create configmap --namespace=ignite ignite-config \
	cd k8s && \
		for manifest in ignite-namespace.yaml \
						ignite-account-role.yaml \
						ignite-role-binding.yaml \
						ignite-service-account.yaml \
						ignite-persistence-storage-class.yaml \
						ignite-service.yaml \
						ignite-stateful-set.yaml; \
		do \
			kubectl apply -f $$manifest; \
		done
