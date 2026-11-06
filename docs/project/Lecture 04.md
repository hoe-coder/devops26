## Lecture 04

Here are the commands for starting, cleanup and stopping the docker instance.

Starting
```shell
docker run -d \
  --name team2-app \
  --restart unless-stopped \
  -p 8080:8080 \
  10.0.40.171:5000/team2/app:${IMAGE_TAG}
```

Cleanup
```shell
# remove dangling images from previous builds
docker image prune -f

# remove old team2/app images, keep the 3 most recent
docker images 10.0.40.171:5000/team2/app --format '{{.ID}}' | tail -n +4 | xargs -r docker rmi -f
```
Stopping

```shell
docker stop team2-app 2>/dev/null || true
docker rm team2-app 2>/dev/null || true
```

For naming the conainter and starting it use following command:

```shell
docker run -d --name team2-app -p 8080:8080 10.0.40.171:5000/team2/app:latest
```