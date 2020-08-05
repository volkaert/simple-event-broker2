#!/bin/bash
xdg-open http://localhost:3000
docker run \
    -p 3000:3000 \
    --user `id -u` \
    --volume `pwd`/grafana-data:/var/lib/grafana \
    --name=grafana \
    grafana/grafana
