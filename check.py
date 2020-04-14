#!/usr/bin/python3

"""
REALLY quick and dirty / fast-forward / success oriented zone awareness checker
"""

# Only stdlib is used!

import argparse
import json
from collections import defaultdict as ddict
from pathlib import Path
from urllib.request import urlopen
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("cache", help="Cache name to check")
parser.add_argument("--base-url", default="http://localhost:8080",
                    help="Ingite's REST API endpoint. Default: %(default)s")

args = parser.parse_args()

BASE_URL = f"{args.base_url}/ignite?"
TOPOLOGY_URL = BASE_URL + "cmd=top&attr=true"
NODE_URL = "&".join((
    BASE_URL,
    "cmd=exe",
    "name=org.apache.ignite.internal.visor.compute.VisorGatewayTask",
    "p1={}",
    "p2=org.apache.ignite.internal.visor.node.VisorNodeConfigurationCollectorTask",
    "p3=java.lang.Void",
)).replace("&", "", 1)
PARTITIONS_URL = "&".join((
    BASE_URL,
    "cmd=exe",
    "name=org.apache.ignite.internal.visor.compute.VisorGatewayTask",
    "p1={node_ids}",
    "p2=org.apache.ignite.internal.visor.cache.VisorCachePartitionsTask",
    "p3=org.apache.ignite.internal.visor.cache.VisorCachePartitionsTaskArg",
    "p4={cache}",
)).replace("&", "", 1)


cache_name = Path(sys.argv[1])

print("Nodes")
zone_map = {}
node_ids = []
for node_info in json.loads(urlopen(TOPOLOGY_URL).read())["response"]:
    node_id = node_info["nodeId"]
    node_ids.append(node_id)
    nid = node_id.partition("-")[0].upper()
    node_details = json.loads(urlopen(NODE_URL.format(node_id)).read())
    zone = node_details["response"]["result"]["env"]["AVAILABILITY_ZONE"]
    zone_map[nid] = zone
    print(f"  {nid}  {zone}")

url = PARTITIONS_URL.format(node_ids=";".join(node_ids), cache=args.cache)
partition_data = json.loads(urlopen(url).read())

pzones = ddict(set)
node_stats = dict()

for node, data in partition_data["response"]["result"].items():
    nid = node.partition("-")[0].upper()
    for ptype in ["primary", "backup"]:
        for part_id in data[ptype].keys():
            pzones[part_id].add(zone_map[nid])

total_partitions = 0
colocation = False
for part_id, zones in pzones.items():
    total_partitions += 1
    if len(zones) < 2:
        print(f"Partition {part_id} is colocated in zone {', '.join(zones)}")
        colocation = True
print(f"Total partitions: {total_partitions}")
if not colocation:
    print("No colocations found - good!")
