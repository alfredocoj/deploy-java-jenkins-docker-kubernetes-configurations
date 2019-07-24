#!/bin/sh

REPO_NAME=$1
IMAGE_NAME=$2
IMAGE_VERSION=$3

appName() {
	echo "$(find . -name *.jar)" | cut -d / -f 2
}

cd target

rm -rf m2

java -jar $(appName) --thin.root=m2 --thin.dryrun

rm -rf dockerBuild
mkdir dockerBuild

cp --recursive m2 dockerBuild/m2
cp $(appName) dockerBuild/app.jar
cp ${DOCKERFILE} dockerBuild/Dockerfile

cd dockerBuild

/var/jenkins_home/tools/org.jenkinsci.plugins.docker.commons.tools.DockerTool/docker/bin/docker build --tag="$REPO_NAME/$IMAGE_NAME:$IMAGE_VERSION" .
/var/jenkins_home/tools/org.jenkinsci.plugins.docker.commons.tools.DockerTool/docker/bin/docker tag "$REPO_NAME/$IMAGE_NAME:$IMAGE_VERSION" "$REPO_NAME/$IMAGE_NAME:latest"
/var/jenkins_home/tools/org.jenkinsci.plugins.docker.commons.tools.DockerTool/docker/bin/docker push "$REPO_NAME/$IMAGE_NAME:$IMAGE_VERSION"
/var/jenkins_home/tools/org.jenkinsci.plugins.docker.commons.tools.DockerTool/docker/bin/docker push "$REPO_NAME/$IMAGE_NAME:latest"