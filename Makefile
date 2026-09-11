DOCKERHUB_USER ?= raselmahmudbits
TAG ?= latest
PLATFORMS ?= linux/amd64

.PHONY: build-images push-images tag-images login help

help:
	@echo "Available targets:"
	@echo "  make login              Docker Hub login"
	@echo "  make build-images TAG=1.0.2       Build only, do not push"
	@echo "  make push-images TAG=1.0.2        Build and push to Docker Hub"
	@echo "  make tag-commit         Build and push tagged with short git SHA"

login:
	docker login

build-images:
	DOCKERHUB_USER=$(DOCKERHUB_USER) TAG=$(TAG) PLATFORMS=$(PLATFORMS) PUSH=0 scripts/push-images.sh $(TAG)

push-images:
	DOCKERHUB_USER=$(DOCKERHUB_USER) TAG=$(TAG) PLATFORMS=$(PLATFORMS) scripts/push-images.sh $(TAG)

tag-commit:
	DOCKERHUB_USER=$(DOCKERHUB_USER) TAG=$$(git rev-parse --short HEAD) PLATFORMS=$(PLATFORMS) scripts/push-images.sh
